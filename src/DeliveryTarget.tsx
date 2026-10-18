import { useEffect, useRef, type RefObject } from 'react';
import { View, type ViewProps } from 'react-native';

import type { TransitionRect } from './types';

const registry = new Map<number, RefObject<View | null>>();

/**
 * Wrap up to 5 elements of the DESTINATION screen (header, cards...) with `<DeliveryTarget index={0..4}>`.
 * The delivery transitions then throw their parcels exactly at these elements and un-box them there.
 * Without any target the screen is simply cut into 5 bands.
 */
export function DeliveryTarget({ index, children, ...rest }: ViewProps & { index: number }) {
  const ref = useRef<View>(null);
  useEffect(() => {
    registry.set(index, ref);
    return () => {
      if (registry.get(index) === ref) registry.delete(index);
    };
  }, [index]);
  return (
    <View ref={ref} collapsable={false} {...rest}>
      {children}
    </View>
  );
}

/** Rectangles of the mounted targets, in dp, in the window. */
export async function measureTargets(): Promise<TransitionRect[]> {
  const entries = [...registry.entries()].sort((a, b) => a[0] - b[0]);
  const rects = await Promise.all(
    entries.map(
      ([, ref]) =>
        new Promise<TransitionRect | null>((resolve) => {
          const view = ref.current;
          if (!view) {
            resolve(null);
            return;
          }
          view.measureInWindow((x, y, width, height) =>
            resolve(width > 0 && height > 0 ? { x, y, width, height } : null)
          );
        })
    )
  );
  return rects.filter((r): r is TransitionRect => r !== null).slice(0, 5);
}
