import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { transitionScreenOptions, useTransitionBackHandler } from 'expo-delivery-transitions';

export default function RootLayout() {
  // Hardware back: skips a running transition. Our quick fade is used only on the screens that have no native animation.
  useTransitionBackHandler({ fadeBack: (pathname) => pathname.startsWith('/item/') });

  return (
    <>
      <StatusBar style="dark" />
      <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: '#F4F4F8' } }}>
        {/* Only THIS screen opts in: its native animation is off because our transition is the animation. */}
        <Stack.Screen name="item/[id]" options={transitionScreenOptions} />
        {/* Every other screen (index, plain) keeps Expo Router's default animation and gestures. */}
      </Stack>
    </>
  );
}
