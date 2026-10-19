import { Platform, processColor, type GestureResponderEvent } from 'react-native';

import { anyTargetLoading, measureTargets } from './DeliveryTarget';
import { Native } from './NativeTransitions';
import { setLoadedHandler } from './loadingRefresh';
import { startReadinessRun } from './transitionReady';
import type { TransitionOptions } from './types';

let running = false;
let tipShown = false;

const nextFrame = () => new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/** Position of a touch (dp), ready to be used as `origin`. */
export function originFromEvent(e: GestureResponderEvent) {
  return { x: e.nativeEvent.pageX, y: e.nativeEvent.pageY };
}

export function isTransitionRunning(): boolean {
  return running;
}

/** Tap-to-skip from JavaScript (the overlay also skips on any tap by itself). */
export function skipTransition(): void {
  Native?.skip();
}

const asColor = (value: string, fallback: number) => {
  const c = processColor(value);
  return typeof c === 'number' ? c : fallback;
};

/**
 * Runs `navigate` (router.push / replace / back...) underneath a native transition.
 *
 *  1. the current screen is photographed and frozen under a full-screen overlay; the delivery vehicle starts at once
 *  2. `navigate()` runs: Expo Router does its normal work (routes, params, layouts, deep links...)
 *  3. the native side tells us when the new screen is REALLY on screen (view tree / picture changed), then we photograph it
 *  4. the native animation plays between the two photos, then the overlay disappears
 *
 * Without the native module (web, Expo Go) or if the animation fails for any reason, the navigation simply happens
 * without animation: an animation problem can never lose a navigation.
 */
export async function runTransition(
  options: TransitionOptions,
  navigate: () => unknown
): Promise<void> {
  if (!Native) {
    await navigate();
    return;
  }
  if (running) return; // a transition is already playing: ignore a second tap instead of navigating twice
  running = true;
  const native = Native;
  const t0 = Date.now();
  let last = t0;
  const mark = (label: string) => {
    if (!options.debug) return;
    const now = Date.now();
    console.log(`[expo-delivery-transitions] ${label}: +${now - last} ms (total ${now - t0} ms)`);
    last = now;
  };
  // nothing can keep the app blocked for ever, even if the native side never answers
  const failsafe = setTimeout(() => {
    native.cancel();
    running = false;
  }, 15000);
  const readiness = startReadinessRun();
  try {
    const name = options.name ?? 'fade';
    const accent = asColor(options.accent ?? '#7C5CFF', 0xff7c5cff);
    const background = asColor(options.background ?? '#000000', 0xff000000);
    const wantsTargets = name === 'deliveryBike' || name === 'deliveryCar';
    try {
      await native.begin();
      mark('screen frozen');
      try {
        // the vehicle starts driving NOW and waits, instead of the screen looking frozen while React renders
        native.preroll?.(JSON.stringify({ name, accent, background }));
      } catch {
        // not available: the animation simply starts when the new screen is ready
      }
    } catch {
      await navigate(); // could not freeze the screen: navigate normally
      return;
    }

    const renderStart = Date.now();
    try {
      await navigate();
      mark('navigate() returned');
    } catch (error) {
      native.cancel();
      throw error;
    }

    try {
      if (typeof native.screenReady === 'function') {
        // the native side watches the real screen (cheaply) and answers when the new one is on screen
        // delivery: the screen may be slow to render (up to 3 s); other transitions (a quick fade back) never wait long
        const shown = await native.screenReady(
          options.screenTimeoutMs ?? (wantsTargets ? 3000 : 700)
        );
        if (shown === false) {
          // the new screen never showed up in the picture: do NOT animate a stale picture (it would show the old
          // screen again). The real screen is there anyway: just remove the overlay.
          mark('new screen not detected: no animation');
          native.cancel();
          return;
        }
        await nextFrame(); // let the new screen register its <DeliveryTarget>s
      } else {
        await nextFrame();
        await nextFrame();
        await wait(options.settleMs ?? (Platform.OS === 'android' ? 30 : 120));
      }
      mark('new screen rendered');
      const renderMs = Date.now() - renderStart;
      if (
        renderMs > 700 &&
        !options.debug &&
        typeof __DEV__ !== 'undefined' &&
        __DEV__ &&
        !tipShown
      ) {
        tipShown = true;
        console.log(
          `[expo-delivery-transitions] the new screen needed ${renderMs} ms to render (JavaScript is the slow part). ` +
            'Debug builds are much slower than release ones: compare with "npx expo run:android --variant release".'
        );
      }

      const ready = await readiness.waitUntilReady(options.readyTimeoutMs ?? 400);
      mark(ready ? 'new screen ready' : 'ready timeout (played anyway)');

      const targets = options.targets ?? (wantsTargets ? await measureTargets() : []);
      mark(`targets measured (${targets.length})`);

      // Elements that are still loading: the vehicle stops at the first one and goes on when ALL data has arrived
      if (targets.some((t) => t.loading) && typeof native.refresh === 'function') {
        let scheduled = false;
        const goOn = () => {
          if (scheduled || anyTargetLoading()) return;
          scheduled = true;
          (async () => {
            await nextFrame();
            await nextFrame();
            await wait(60); // let the new data be drawn before it is photographed
            mark('data arrived, vehicle goes on');
            try {
              native.refresh?.();
            } catch {
              // nothing to refresh
            }
          })().catch(() => {});
        };
        setLoadedHandler(goOn);
        goOn(); // the data may have arrived while the targets were being measured
      }

      await native.play(
        JSON.stringify({
          name,
          originX: options.origin?.x ?? 0,
          originY: options.origin?.y ?? 0,
          accent,
          background,
          durationMs: options.durationMs ?? 0,
          targets,
        })
      );
      mark('animation finished');
    } catch {
      native.cancel(); // the screen is already the new one; just remove the overlay
    }
  } finally {
    clearTimeout(failsafe);
    setLoadedHandler(null);
    readiness.end();
    running = false;
  }
}
