import { useEffect, useRef, type RefObject } from 'react';
import { Dimensions, View, type ViewProps } from 'react-native';

import { notifyLoaded } from './loadingRefresh';
import type { TransitionRect } from './types';

type Entry = { ref: RefObject<View | null>; loading: boolean };
const registry = new Map<number, Entry>();

/** True while any mounted target says its data is still loading. */
export function anyTargetLoading(): boolean {
  for (const entry of registry.values()) if (entry.loading) return true;
  return false;
}

/**
 * Wrap up to 5 elements of the DESTINATION screen (header, cards, list...) with `<DeliveryTarget index={0..4}>`.
 * The delivery transitions throw their parcels exactly at these elements; the screen is cut into slices between them.
 *
 * `loading`: the element is still waiting for its data (fetch). The vehicle drives to it, STOPS there (with "?"
 * bubbles) and goes on the moment `loading` becomes false, so a slow request never delays the start.
 * Without any target the screen is simply cut into 5 bands.
 */
export function DeliveryTarget({
  index,
  loading = false,
  children,
  ...rest
}: ViewProps & { index: number; loading?: boolean }) {
  const ref = useRef<View>(null);
  const entry = useRef<Entry>({ ref, loading });
  useEffect(() => {
    const mine = entry.current;
    registry.set(index, mine);
    return () => {
      if (registry.get(index) === mine) registry.delete(index);
    };
  }, [index]);
  useEffect(() => {
    entry.current.loading = loading;
    if (!loading) notifyLoaded();
  }, [loading]);
  return (
    <View ref={ref} collapsable={false} {...rest}>
      {children}
    </View>
  );
}

/** Keeps only the part of every element that is on screen (a long list is taller than the screen), top to bottom. */
export function clipTargets(
  rects: TransitionRect[],
  screenHeight: number,
  minSize = 24
): TransitionRect[] {
  return rects
    .map((r) => {
      const top = Math.max(0, r.y);
      const bottom = Math.min(screenHeight, r.y + r.height);
      return { ...r, y: top, height: bottom - top };
    })
    .filter((r) => r.height >= minSize && r.width >= minSize)
    .sort((a, b) => a.y - b.y);
}

/**
 * The delivery animation throws 5 parcels. With 1 to 4 targets (a screen whose "Add" button is hidden, a list with a
 * single item...) the tallest ones are split in half until there are 5, so the screen never falls back to equal bands.
 */
export function fillTargets(rects: TransitionRect[], count = 5): TransitionRect[] {
  if (rects.length === 0 || rects.length >= count) return rects.slice(0, count);
  const out = rects.map((r) => ({ ...r }));
  while (out.length < count) {
    let k = 0;
    for (let i = 1; i < out.length; i++) if (out[i].height > out[k].height) k = i;
    const r = out[k];
    const half = r.height / 2;
    out.splice(k, 1, { ...r, height: half }, { ...r, y: r.y + half, height: half });
  }
  return out;
}

/** Rectangles of the mounted targets, in dp, in the window. */
export async function measureTargets(): Promise<TransitionRect[]> {
  const entries = [...registry.entries()].sort((a, b) => a[0] - b[0]);
  const rects = await Promise.all(
    entries.map(
      ([, { ref, loading }]) =>
        new Promise<TransitionRect | null>((resolve) => {
          const view = ref.current;
          if (!view) {
            resolve(null);
            return;
          }
          view.measureInWindow((x, y, width, height) =>
            resolve(width > 0 && height > 0 ? { x, y, width, height, loading } : null)
          );
        })
    )
  );
  const onScreen = clipTargets(
    rects.filter((r): r is TransitionRect => r !== null),
    Dimensions.get('window').height
  );
  return fillTargets(onScreen);
}
