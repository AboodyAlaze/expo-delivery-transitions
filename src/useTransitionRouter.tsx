import { Link, usePathname, useRouter, type Href, type LinkProps } from 'expo-router';
import { useEffect, useMemo, useRef } from 'react';
import {
  BackHandler,
  Pressable,
  View,
  type GestureResponderEvent,
  type NativeEventSubscription,
  type PressableProps,
} from 'react-native';

import {
  isTransitionRunning,
  originFromEvent,
  runTransition,
  skipTransition,
} from './runTransition';
import type { TransitionOptions } from './types';

type Router = ReturnType<typeof useRouter>;
/** Expo Router's own second argument of push / navigate / replace (e.g. `withAnchor`). */
type NavOptions = NonNullable<Parameters<Router['push']>[1]>;
type ForwardOptions = TransitionOptions & NavOptions;

/** Splits "transition options + Expo Router's own options" so each one reaches the right place. */
function split(options?: ForwardOptions): { transition: TransitionOptions; nav: NavOptions } {
  const {
    name,
    origin,
    accent,
    durationMs,
    targets,
    settleMs,
    readyTimeoutMs,
    debug,
    background,
    screenTimeoutMs,
    originRect,
    ...nav
  } = options ?? {};
  return {
    transition: {
      name,
      origin,
      accent,
      durationMs,
      targets,
      settleMs,
      readyTimeoutMs,
      debug,
      background,
      screenTimeoutMs,
      originRect,
    },
    nav: nav as NavOptions,
  };
}

/**
 * Same API as Expo Router's `useRouter()`. `push`, `navigate`, `replace` and `dismissTo` accept an extra
 * transition (`{ name: 'deliveryBike' ... }`) next to Expo Router's own options; `back`, `dismiss` and `dismissAll`
 * go back with a quick cross-fade. Everything else (params, canGoBack, setParams, prefetch...) is Expo Router's own.
 *
 * Using the plain `useRouter()` / `<Link>` keeps working exactly as before: only calls that go through this hook get a
 * transition.
 */
export function useTransitionRouter(defaults: TransitionOptions = {}) {
  const router = useRouter();
  const { name, accent, durationMs } = defaults;
  return useMemo(() => {
    const withDefaults = (t: TransitionOptions): TransitionOptions => ({
      accent,
      durationMs,
      ...t,
      name: t.name ?? name ?? 'deliveryBike',
    });
    const forward =
      (fn: (href: Href, options?: NavOptions) => void) =>
      (href: Href, options?: ForwardOptions) => {
        const { transition, nav } = split(options);
        return runTransition(withDefaults(transition), () => fn(href, nav));
      };
    return {
      ...router,
      push: forward(router.push),
      navigate: forward(router.navigate),
      replace: forward(router.replace),
      dismissTo: forward(router.dismissTo),
      // going BACK is a quick "slide away" (300 ms): nobody is made to wait
      back: (options?: TransitionOptions) =>
        runTransition({ name: 'dismiss', ...options }, () => router.back()),
      dismiss: (count?: number, options?: TransitionOptions) =>
        runTransition({ name: 'dismiss', ...options }, () => router.dismiss(count)),
      dismissAll: (options?: TransitionOptions) =>
        runTransition({ name: 'dismiss', ...options }, () => router.dismissAll()),
    };
  }, [router, name, accent, durationMs]);
}

type BackHandlerOptions = {
  /**
   * Also run hardware back with the quick cross-fade, when there is somewhere to go back to.
   * `true` = on every screen, or a function that receives the current pathname, e.g. `(p) => p.startsWith('/item/')`.
   *
   * Default `false`: hardware back is then 100% Expo Router's own (native animation included), except that it skips a
   * running transition. Use it for screens that have `transitionScreenOptions` (native animation off), because
   * otherwise their back would be instant.
   */
  fadeBack?: boolean | ((pathname: string) => boolean);
};

/**
 * Call it ONCE in your root layout.
 *  - hardware back while a transition plays -> skips it
 *  - hardware back otherwise                -> Expo Router as usual (or the quick fade, see `fadeBack`)
 *
 * React Native calls back-button listeners in REVERSE order of registration, and React Navigation registers its own
 * listener (in a parent, so after the children's effects). We register one tick later so that OURS runs first.
 */
export function useTransitionBackHandler({ fadeBack = false }: BackHandlerOptions = {}) {
  const router = useRouter();
  const pathname = usePathname();
  useEffect(() => {
    let subscription: NativeEventSubscription | undefined;
    const timer = setTimeout(() => {
      subscription = BackHandler.addEventListener('hardwareBackPress', () => {
        if (isTransitionRunning()) {
          skipTransition();
          return true;
        }
        const fade = typeof fadeBack === 'function' ? fadeBack(pathname) : fadeBack;
        if (fade && router.canGoBack()) {
          runTransition({ name: 'dismiss' }, () => router.back());
          return true;
        }
        return false; // not ours: Expo Router handles it as usual
      });
    }, 0);
    return () => {
      clearTimeout(timer);
      subscription?.remove();
    };
  }, [router, fadeBack, pathname]);
}

type TransitionPressableProps = Omit<PressableProps, 'onPress'> & {
  href: Href;
  transition?: TransitionOptions;
  replace?: boolean;
  onPress?: (e: GestureResponderEvent) => void;
};

/** A Pressable that navigates with a transition that starts exactly where the finger touched. */
export function TransitionPressable({
  href,
  transition,
  replace,
  onPress,
  ...rest
}: TransitionPressableProps) {
  const router = useTransitionRouter();
  const ref = useRef<View>(null);
  return (
    <Pressable
      {...rest}
      ref={ref}
      onPress={(e) => {
        onPress?.(e);
        const options: TransitionOptions = { origin: originFromEvent(e), ...transition };
        const go = (o: TransitionOptions) => {
          if (replace) router.replace(href, o);
          else router.push(href, o);
        };
        if (options.name === 'cardExpand' && !options.originRect && ref.current) {
          // the card grows from this very element: measure it first
          ref.current.measureInWindow((x, y, width, height) =>
            go({ ...options, originRect: { x, y, width, height } })
          );
        } else {
          go(options);
        }
      }}
    />
  );
}

type TransitionLinkProps = LinkProps & { transition?: TransitionOptions };

/**
 * Drop-in for Expo Router's `<Link>` (same props, `asChild` included) that navigates with a transition.
 * External links and `dismissTo` links are left to the normal `<Link>`.
 */
export function TransitionLink({ transition, onPress, ...props }: TransitionLinkProps) {
  const router = useTransitionRouter();
  const href = props.href;
  const external = typeof href === 'string' && /^[a-z][a-z0-9+.-]*:/i.test(href);
  const intercept = !external && !props.dismissTo;

  return (
    <Link
      {...props}
      onPress={(e) => {
        onPress?.(e);
        const event = e as GestureResponderEvent & { defaultPrevented?: boolean };
        if (!intercept || event.defaultPrevented) return;
        // <Link> skips its own navigation when the event is default-prevented (Expo Router 57, native)
        event.preventDefault();
        const options: TransitionOptions = { origin: originFromEvent(event), ...transition };
        const go = (o: TransitionOptions) => {
          if (props.replace) router.replace(href, o);
          else router.push(href, o);
        };
        const target = (event as unknown as { currentTarget?: { measureInWindow?: unknown } })
          .currentTarget;
        if (
          options.name === 'cardExpand' &&
          !options.originRect &&
          typeof target?.measureInWindow === 'function'
        ) {
          // the card grows from the pressed element: measure it first
          (
            target.measureInWindow as (
              cb: (x: number, y: number, w: number, h: number) => void
            ) => void
          )((x, y, width, height) => go({ ...options, originRect: { x, y, width, height } }));
        } else {
          go(options);
        }
      }}
    />
  );
}
