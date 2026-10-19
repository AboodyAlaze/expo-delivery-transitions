# expo-delivery-transitions

Creative **native page transitions for Expo Router** on **Android and iOS**:

`deliveryBike` · `deliveryCar` · `liquidPour` · `ballZoom` · `radarSweep` · `hexAperture` · `curtainDrop` · `fade`

The delivery transitions have a courier (bike) or a delivery car — with **your logo** on it — that *delivers* the new
screen: parcels are thrown at the real cards of the destination screen and un-box into them.

## It does not change Expo Router

Everything is **opt-in**. Nothing is replaced, patched or monkey-patched:

| | |
|---|---|
| Routes, layouts, groups, params, typed routes, deep links, redirects | untouched |
| `<Link>`, `router.push / replace / back / navigate / dismiss` from `expo-router` | untouched (default native animation, iOS swipe-back, Android back button) |
| Tabs, modals, drawers, nested navigators | untouched |
| Screens you did **not** opt in | untouched (default animation and gestures) |

A transition only happens when you navigate **through this package** (`useTransitionRouter`, `<TransitionLink>`,
`<TransitionPressable>`), and the native animation is switched off only on the screens you list in `transitionScreenOptions`.

## Install

```bash
npx expo install expo-delivery-transitions
npx expo prebuild   # or: eas build
```

It's a native module: it needs a development build or EAS build (not Expo Go). Expo SDK 57+. Android and iOS (16.4+).

## Setup (once)

```tsx
// app/_layout.tsx
import { Stack } from 'expo-router';
import { transitionScreenOptions, useTransitionBackHandler } from 'expo-delivery-transitions';

export default function RootLayout() {
  // hardware back: skips a running transition (everything else is Expo Router's own)
  useTransitionBackHandler();

  return (
    <Stack>
      {/* opt-in: this screen has no native animation, because our transition IS the animation */}
      <Stack.Screen name="item/[id]" options={transitionScreenOptions} />
      {/* every other screen keeps Expo Router's default animation and gestures */}
    </Stack>
  );
}
```

Why the opt-in? The new screen is photographed right after it is shown; a native slide-in running at that moment would
be photographed half-way. So the screens you navigate to *with a transition* get `animation: 'none'`.

## Use

```tsx
import {
  TransitionLink, TransitionPressable, useTransitionRouter, originFromEvent,
} from 'expo-delivery-transitions';

// 1) a drop-in <Link> (same props, `asChild` included)
<TransitionLink href="/item/42" asChild transition={{ name: 'liquidPour', accent: '#00A884' }}>
  <Pressable><Text>Open</Text></Pressable>
</TransitionLink>

// 2) a Pressable; the animation starts exactly where the finger touched
<TransitionPressable href="/item/42" transition={{ name: 'ballZoom' }}>
  <Text>Open</Text>
</TransitionPressable>

// 3) the router: same API as useRouter(), push / navigate / replace / dismissTo take a transition
const router = useTransitionRouter();
router.push('/item/42', { name: 'deliveryBike', accent: '#F7BE01', origin: originFromEvent(e), withAnchor: true });
router.back();   // quick "slide away" (300 ms): nobody is made to wait
```

`router.push(href, options)` takes the transition options **and** Expo Router's own options (`withAnchor`...) in the same
object. `back`, `dismiss` and `dismissAll` use the quick `dismiss` animation (the screen you leave slides down and fades).

### Back on the opted-in screens

Those screens have no native animation, so a plain back (`useRouter().back()`) is instant. Use the transition router in your back button, and animate the hardware back button too:

```tsx
useTransitionBackHandler({ fadeBack: (pathname) => pathname.startsWith('/item/') });
```
(`fadeBack: true` = every screen. Default `false`.) In your own back buttons use `const { back } = useTransitionRouter();`.

### Delivery transitions: tell them where the elements are

Wrap up to **5 elements of the destination screen** with `<DeliveryTarget index={0..4}>` (top to bottom). The courier
throws a parcel at each one and the screen opens there.

The new screen is cut into **full-width slices that together cover the whole screen**: no gaps, no lines. Each
boundary is halfway between two neighbouring elements, and every slice opens where its parcel lands.

```tsx
<DeliveryTarget index={0}><Header /></DeliveryTarget>
<DeliveryTarget index={1}><Stats /></DeliveryTarget>
<DeliveryTarget index={2}><AddButton /></DeliveryTarget>
<DeliveryTarget index={3}><List /></DeliveryTarget> {/* a long list: only the part on screen is used */}
```

- Fewer than 5 targets are fine (the tallest are split until there are 5). Without any target the screen is cut into 5
  equal slices.
- Elements taller than the screen are clipped to the screen.
- Avoid entering animations (Reanimated `FadeIn` with a delay...) on the first content of the screen: it is photographed
  right after it appears, so content that is still invisible at that moment is missing in the picture.

### Loading: the vehicle stops where the data is still coming

Say that a target is still loading and the vehicle drives to it, **stops there** (with "?" bubbles) and goes on the
moment the data has arrived. A slow request never delays the start of the animation, and the header, the numbers... are
already revealed while the list is still loading.

```tsx
const { data, isLoading } = useFetch(/* ... */);

<DeliveryTarget index={0}><Header /></DeliveryTarget>
<DeliveryTarget index={2} loading={isLoading || !data}>
  <List data={data} />
</DeliveryTarget>
```

Draw the real content as soon as the data is there (no extra delays): a fresh picture of the screen is taken at that
moment. If the data never comes the vehicle goes on after 3.5 s. Items thrown before the stop keep opening while the
vehicle waits.

### Options

| option | |
|---|---|
| `name` | `deliveryBike` \| `deliveryCar` \| `liquidPour` \| `ballZoom` \| `radarSweep` \| `hexAperture` \| `curtainDrop` \| `fade` |
| `origin` | `{ x, y }` in dp (`originFromEvent(e)`). Radar, hex and ball zoom start exactly there |
| `accent` | colour of the tapped item: liquid, ball, radar, parcel tape… |
| `background` | solid colour behind the animation, default `'#000000'` (nothing is see-through) |
| `durationMs` | override the default duration (delivery: bike 1200 ms, car 1400 ms) |
| `targets` | delivery only: `{ x, y, width, height, loading? }[]` (normally filled from `<DeliveryTarget>`) |
| `screenTimeoutMs` | max wait until the new screen is really on screen (default 3000 for delivery, 700 otherwise) |
| `readyTimeoutMs` | max wait for `useTransitionReady` (default 400) |
| `debug` | prints the time of every phase in the console |

**Tap anywhere** while a transition plays to skip it. Hardware back skips it too.

`useTransitionReady(ready)` (optional) makes the transition wait, at most `readyTimeoutMs`, until the new screen says it
is ready. It is slower on purpose: prefer `loading` on a `<DeliveryTarget>`, which never delays the start.

## How it works

1. `begin()`: the current screen is photographed and frozen under a full-screen overlay (the old screen fades to solid
   black) and the vehicle slides in at once.
2. Expo Router navigates underneath, as usual. The vehicle waits at the top with "?" bubbles.
3. `screenReady()`: the native side watches the real screen and tells JavaScript when the NEW one is on screen. It
   counts the views (about 1 ms) and takes a tiny 45 x 100 picture every 100 ms; a pressed button or a fading tab bar
   does not count, a different screen does. No full screenshots are taken while waiting, so this never competes with
   the work that is mounting the new screen.
4. `play()`: the new screen is photographed and the animation plays; elements that are still loading stop the vehicle
   until `refresh()` brings a fresh picture.
5. The overlay is removed and its two pictures are freed. The real new screen is already there and fully interactive.

**Android**: no Jetpack Compose; a plain `View` drawn with `Canvas` and a `ValueAnimator`.
**iOS**: a plain `UIView`; the screen slices and parcels are Core Animation layers moved on the GPU, only the small
vehicle view is redrawn each frame. Same animations, same JavaScript API, same features (waiting, loading, background).

Safety: a watchdog removes the overlay no matter what, and if an animation fails the navigation still happens (an
animation problem can never lose a navigation). On web and Expo Go the navigation just happens, without animation.

## Speed

- The wait before the delivery runs is the time JavaScript needs to render the new screen. **Debug builds are several
  times slower than release builds**: judge the speed with `npx expo run:android --variant release` / `npx expo run:ios --configuration Release`.
- Use `debug: true` to see where the time goes (`screen frozen`, `navigate() returned`, `new screen rendered`, ...).
- If a request is slow, mark its element with `loading` instead of waiting for it.

## Troubleshooting

- **The new screen shows up before the animation / the picture of the old screen is the new page.** One tap opens the
  screen twice: a plain `router.push` / `<Link>` next to the transition call. Keep only the transition one.
- **The slices show the old screen.** The screen took longer than `screenTimeoutMs` to mount; raise it, or compare with a
  release build.
- **Both my own animation and the transition play.** The destination screen needs `options={transitionScreenOptions}`.

## Try the example

```bash
cd example
npm install

npx expo run:android --device   # Android: USB debugging on, phone plugged in (or an emulator without --device)
npx expo run:ios                # iOS simulator (needs Xcode + CocoaPods)
npx expo run:ios --device       # a real iPhone (Xcode signing with your Apple ID is enough)
```

The example resolves the package from the parent folder (`metro.config.js` + `expo.autolinking.nativeModulesDir`), so
edits to `src/` show up instantly. It also has a plain `<Link>` screen, to see that the default navigation is untouched.

## Notes & limits

- Screens that contain a `SurfaceView` / `TextureView` (maps, video, camera) are photographed black.
- While a transition plays, two screen-sized pictures are kept in memory (freed when it ends).
- `origin` / `targets` are relative to the React root view (what Expo Router renders), in dp / points.
- Android minSdk 24, iOS 16.4+.
- Modals (`presentation: 'modal'`) are not photographed on iOS (they live in their own layer); opt them in only if you test them.
- The iOS code was type-checked with a Swift compiler against UIKit stubs but has not been built in Xcode here: please test it on a device.

## License

MIT
