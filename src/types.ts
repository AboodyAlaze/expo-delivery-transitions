export type TransitionName =
  | 'deliveryBike'
  | 'deliveryCar'
  | 'deliveryBikeBag'
  | 'deliveryCarBag'
  | 'cardExpand'
  | 'liquidPour'
  | 'ballZoom'
  | 'radarSweep'
  | 'hexAperture'
  | 'curtainDrop'
  | 'dismiss'
  | 'fade';

export type TransitionRect = {
  x: number;
  y: number;
  width: number;
  height: number;
  /** The element is still loading its data: the vehicle stops there until the data arrives. */
  loading?: boolean;
};

export type TransitionOptions = {
  /** Which animation. Default: 'fade'. (`back`, `dismiss` and `dismissAll` use 'dismiss'.) */
  name?: TransitionName;
  /** Where the user touched, in dp (see `originFromEvent`). Radar / hex / ball zoom start exactly there. */
  origin?: { x: number; y: number };
  /**
   * The tapped element in dp (`measureInWindow`), for `cardExpand`: the card grows from this rectangle. `TransitionPressable`
   * measures itself; with `router.push` pass it yourself (without it the card grows around the touch point).
   */
  originRect?: TransitionRect;
  /** Colour of the tapped item (liquid, ball, radar, parcel tape...). Any React Native colour. */
  accent?: string;
  /** 0 / undefined = the default duration of that transition. */
  durationMs?: number;
  /**
   * Delivery transitions only: rectangles (dp, in the window) of up to 5 elements of the NEW screen.
   * Normally filled in automatically from the `<DeliveryTarget>` components of the new screen.
   */
  targets?: TransitionRect[];
  /** Max time (ms) to wait until the new screen is really on screen. Default 3000. */
  screenTimeoutMs?: number;
  /** Fallback only (old native builds): time to wait for the new screen to render before it is photographed. */
  settleMs?: number;
  /**
   * Max time (ms) to wait for the NEW screen to say it is ready (see `useTransitionReady`). Default 400 (speed first).
   * Only matters for screens that call `useTransitionReady`; other screens are never waited for.
   */
  readyTimeoutMs?: number;
  /** Solid colour behind the animation (the old screen fades into it). Default black. */
  background?: string;
  /** Prints the time of every phase (freeze / navigate / render / ready / measure / animation) in the console. */
  debug?: boolean;
};
