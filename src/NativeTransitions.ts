import { requireOptionalNativeModule } from 'expo';
import { Platform } from 'react-native';

export type NativeTransitions = {
  /** Freeze the current screen under the overlay. */
  begin(): Promise<void>;
  /** The new screen is ready: animate. Resolves when the animation ends or is skipped. */
  play(optionsJson: string): Promise<void>;
  /** Start the animation right now (the vehicle arrives and waits) while the new screen renders. */
  preroll?(optionsJson: string): void;
  /**
   * Resolves `true` when the NEW screen is really on screen (what is drawn is clearly another screen), `false` if it
   * never showed up within `timeoutMs`.
   */
  screenReady?(timeoutMs: number): Promise<boolean | void>;
  /** The data of a loading element arrived, take a fresh picture and let the vehicle go on. */
  refresh?(): void;
  skip(): void;
  cancel(): void;
  isRunning(): boolean;
};

/** null on web / Expo Go: everything then falls back to a plain navigation. */
export const Native: NativeTransitions | null =
  Platform.OS === 'android' || Platform.OS === 'ios'
    ? requireOptionalNativeModule<NativeTransitions>('DeliveryTransitions')
    : null;
