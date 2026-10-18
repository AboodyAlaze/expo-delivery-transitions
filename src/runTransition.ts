import { processColor, type GestureResponderEvent } from 'react-native';

import { measureTargets } from './DeliveryTarget';
import { Native } from './NativeTransitions';
import { startReadinessRun } from './transitionReady';
import type { TransitionOptions } from './types';

let running = false;

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

/**
 * Runs `navigate` (router.push / replace / back...) underneath a native transition.
 *
 *  1. the current screen is photographed and frozen under a full-screen overlay
 *  2. `navigate()` runs: Expo Router does its normal work (routes, params, layouts, deep links...)
 *  3. we wait until the new screen has rendered and photograph it
 *  4. the native animation plays between the two photos, then the overlay disappears
 *
 * Without the native module (iOS, web, Expo Go) or if the animation fails for any reason, the navigation simply
 * happens without animation: an animation problem can never lose a navigation.
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
  const t0 = Date.now();
  let last = t0;
  const mark = (label: string) => {
    if (!options.debug) return;
    const now = Date.now();
    console.log(`[expo-delivery-transitions] ${label}: +${now - last} ms (total ${now - t0} ms)`);
    last = now;
  };
  const readiness = startReadinessRun();
  try {
    const name = options.name ?? 'fade';
    const accent = processColor(options.accent ?? '#7C5CFF');
    try {
      await Native.begin();
      mark('screen frozen');
      try {
        // Android: the vehicle starts driving NOW and waits, instead of the screen looking frozen while React renders
        Native.preroll?.(
          JSON.stringify({ name, accent: typeof accent === 'number' ? accent : 0xff7c5cff })
        );
      } catch {
        // not available: the animation simply starts when the new screen is ready
      }
    } catch {
      await navigate(); // could not freeze the screen: navigate normally
      return;
    }

    try {
      await navigate();
      mark('navigate() returned');
    } catch (error) {
      Native.cancel();
      throw error;
    }

    try {
      await nextFrame();
      await nextFrame();
      await wait(options.settleMs ?? 120);
      mark('new screen rendered');

      const ready = await readiness.waitUntilReady(options.readyTimeoutMs ?? 1500);
      mark(ready ? 'new screen ready' : 'ready timeout (played anyway)');

      const wantsTargets = name === 'deliveryBike' || name === 'deliveryCar';
      const targets = options.targets ?? (wantsTargets ? await measureTargets() : []);
      mark(`targets measured (${targets.length})`);

      await Native.play(
        JSON.stringify({
          name,
          originX: options.origin?.x ?? 0,
          originY: options.origin?.y ?? 0,
          accent: typeof accent === 'number' ? accent : 0xff7c5cff,
          durationMs: options.durationMs ?? 0,
          targets,
        })
      );
      mark('animation finished');
    } catch {
      Native.cancel(); // the screen is already the new one; just remove the overlay
    }
  } finally {
    readiness.end();
    running = false;
  }
}
