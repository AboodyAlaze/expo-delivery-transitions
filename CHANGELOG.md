# Changelog

## 0.4.0

- **Fixed: the slices showed the OLD screen (again).** The new screen was accepted as "on screen" as soon as its view
  tree changed, but the new screen's views can be attached while the old screen is still what is DRAWN. The new screen
  now has to be confirmed by a tiny picture of what is drawn (clearly another screen than the frozen one). The view tree
  alone is only trusted for screens that look almost the same as the old one, 700 ms after it changed.
- **If the new screen is never detected, nothing is animated** (the overlay is removed and the real screen shows),
  instead of animating a stale picture. `screenReady()` now answers `true` / `false`.
- **New BACK animation `dismiss`:** the screen you leave slides down, shrinks and fades away while the screen below
  brightens (300 ms), on Android and iOS. `back()`, `dismiss()`, `dismissAll()` and `useTransitionBackHandler` use it.
  (`fade` is still available: `{ name: 'fade' }`.)

## 0.3.1 (performance pass)

- **Android: the parcel (box + tape + logo) is drawn ONCE into a small bitmap** and only moved per frame. Before, the
  logo (about 1000 vector points, two paths) was rebuilt and filled for every parcel in the air on every frame.
- **Cheaper screen watching** (Android + iOS): views are counted every 33 ms (about 1 ms), the tiny picture is taken
  for the first time after 200 ms and then at most once every 250 ms, and slower screens are probed less often (the
  watching is capped at about 4% of one core). It stops the moment the new screen is there.
- Settle after the new screen appears: one frame (was two).

## 0.3.0

- **Fixed: slices showed the OLD screen.** On a slow (debug) build the new screen was photographed before it had
  appeared, so the animation revealed the old screen. The native side now tells JavaScript when the new screen is really
  on screen (view count + a tiny picture, no full screenshots while waiting), instead of guessing with timers.
- **iOS now has everything Android has:** the vehicle starts at once, solid background, full-screen slices, loading
  stops, faster timings, GPU layers for slices and parcels (only the small vehicle view is redrawn per frame).
- **Loading:** `<DeliveryTarget loading>`: the vehicle stops at the element that is still loading and goes on when the
  data arrives; parcels already thrown keep opening while it waits (no more frozen half-open cards).
- **Solid background:** `background` option (default black); the old screen fades into it.
- **Faster:** bike 1.2 s, car 1.4 s (was 2.6 s / 3.2 s); no more `Tap to skip` pill / road line / "Delivered" stamp.
- **Safer:** JavaScript failsafe (15 s), the two full-screen pictures are freed when the overlay is removed.
- New options: `screenTimeoutMs`, `background`, `debug`. New hook: `useTransitionReady`.

## 0.1.x

- First releases (Android + iOS).
