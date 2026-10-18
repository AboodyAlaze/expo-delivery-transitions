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
router.back();   // quick cross-fade: nobody is made to wait
```

`router.push(href, options)` takes the transition options **and** Expo Router's own options (`withAnchor`...) in the same
object. `back`, `dismiss` and `dismissAll` use the quick fade.

### Back on the opted-in screens

Those screens have no native animation, so a plain back is instant. To fade them back with the hardware back button:

```tsx
useTransitionBackHandler({ fadeBack: (pathname) => pathname.startsWith('/item/') });
```
(`fadeBack: true` = every screen. Default `false`.) In your own back buttons use `useTransitionRouter().back()`.

### Delivery transitions: tell them where the cards are

Wrap up to **5 elements of the destination screen** with `<DeliveryTarget index={0..4}>`. The courier throws a parcel
at each one and it un-boxes there. Without targets the screen is cut into 5 bands.

```tsx
<DeliveryTarget index={0} style={styles.header}>…</DeliveryTarget>
<DeliveryTarget index={1} style={styles.stats}>…</DeliveryTarget>
```

### Options

| option | |
|---|---|
| `name` | `deliveryBike` \| `deliveryCar` \| `liquidPour` \| `ballZoom` \| `radarSweep` \| `hexAperture` \| `curtainDrop` \| `fade` |
| `origin` | `{ x, y }` in dp (`originFromEvent(e)`). Radar, hex and ball zoom start exactly there |
| `accent` | colour of the tapped item: liquid, ball, radar, parcel tape… |
| `durationMs` | override the default duration of the transition |
| `targets` | delivery only: `{ x, y, width, height }[]` (normally filled from `<DeliveryTarget>`) |
| `settleMs` | wait for the new screen to render before it is photographed (default 120) |

**Tap anywhere** while a transition plays to skip it. Hardware back skips it too.

## How it works

1. `begin()` — the current screen is photographed and frozen under a full-screen overlay.
2. Expo Router navigates underneath, as usual.
3. `play()` — the new screen is photographed and the animation plays between the two photos on the overlay.
4. The overlay is removed. The real new screen is already there and fully interactive.

**Android**: no Jetpack Compose, a plain `View` drawn with `Canvas` and a `ValueAnimator`.
**iOS**: a plain `UIView`; the screens are Core Animation layers moved by a `CADisplayLink` (GPU), the vehicles,
parcels, glass and radar are drawn with Core Graphics on top. Same animations, same JavaScript API.

Safety: a watchdog removes the overlay no matter what, and if an animation fails the navigation still happens
(an animation problem can never lose a navigation). On web and Expo Go the navigation just happens, without animation.

## Try the example

```bash
cd example
npm install

npx expo run:android --device   # Android: USB debugging on, phone plugged in (or an emulator without --device)
npx expo run:ios                # iOS simulator (needs Xcode + CocoaPods)
npx expo run:ios --device       # a real iPhone (Xcode signing with your Apple ID is enough)
```

The example resolves the package from the parent folder (`metro.config.js` + `expo.autolinking.nativeModulesDir`),
so edits to `src/` show up instantly. It also has a plain `<Link>` screen, to see that the default navigation is untouched.

## Notes & limits

- Screens that contain a `SurfaceView` / `TextureView` (maps, video, camera) are photographed black.
- While a transition plays, two screen-sized bitmaps are kept in memory.
- `origin` / `targets` are relative to the React root view (what Expo Router renders), in dp / points.
- Android minSdk 24, iOS 16.4+.
- iOS draws the vehicle overlay with Core Graphics every frame: smooth on recent iPhones; on the oldest ones the delivery
  transitions can drop frames (the screens themselves always move on the GPU).
- Modals (`presentation: 'modal'`) are not photographed on iOS (they live in their own layer); opt them in only if you test them.

## License

MIT

## Speed and layout (delivery transitions)

- **Fast:** the bike takes about 1.2 s and the car 1.4 s (you can change it with `durationMs`).
- **Starts at once (Android):** the vehicle slides in the moment the user taps, while the new screen renders. The old
  screen stays untouched until the new one is really on screen (the module checks that natively, no guessing).
- **No gaps, no lines:** the new screen is cut into full-width slices that together cover the whole screen. Their
  boundaries come from your `<DeliveryTarget>` elements (halfway between two neighbours); every slice opens where its
  parcel lands. With no targets the screen is cut into 5 equal slices.

```tsx
<DeliveryTarget index={0}><Header /></DeliveryTarget>
<DeliveryTarget index={1}><Stats /></DeliveryTarget>
<DeliveryTarget index={2}><Button /></DeliveryTarget>
<DeliveryTarget index={3}><List /></DeliveryTarget> {/* a long list: only the part on screen is used */}
```

Fewer than 5 targets are fine: the tallest ones are split until there are 5. Elements taller than the screen are clipped
to the screen.

**Tip:** avoid entering animations (for example Reanimated `FadeIn` with a delay) on the first screen content. The new
screen is photographed right after it renders, so content that is still invisible at that moment is missing in the picture.

## Optional

**`useTransitionReady(ready)`** makes the transition wait (at most `readyTimeoutMs`, default 1500) until the new screen
says it is ready. It is SLOWER on purpose: use it only if you prefer a finished picture over speed.

**`debug: true`** prints the time of every phase:

```tsx
router.push('/details', { name: 'deliveryBike', debug: true });
// [expo-delivery-transitions] screen frozen: +25 ms (total 25 ms)
```
