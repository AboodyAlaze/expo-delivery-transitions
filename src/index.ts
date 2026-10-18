export {
  runTransition,
  skipTransition,
  isTransitionRunning,
  originFromEvent,
} from './runTransition';
export { DeliveryTarget } from './DeliveryTarget';
export {
  useTransitionRouter,
  useTransitionBackHandler,
  TransitionPressable,
  TransitionLink,
} from './useTransitionRouter';
export type { TransitionName, TransitionOptions, TransitionRect } from './types';

/**
 * Screen options for the screens you navigate to WITH a transition:
 *
 *     <Stack.Screen name="item/[id]" options={transitionScreenOptions} />
 *
 * The native Stack animation of THAT screen is switched off, because our transition is the animation (otherwise you
 * would see both). Every other screen keeps Expo Router's default animation and gestures: nothing else is touched.
 */
export const transitionScreenOptions = {
  animation: 'none',
} as const;
