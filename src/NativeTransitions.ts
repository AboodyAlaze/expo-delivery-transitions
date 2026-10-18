import { requireOptionalNativeModule } from 'expo';
import { Platform } from 'react-native';

export type NativeTransitions = {
  /** Freeze the current screen under the overlay. */
  begin(): Promise<void>;
  /** The new screen is ready: animate. Resolves when the animation ends or is skipped. */
  play(optionsJson: string): Promise<void>;
  skip(): void;
  cancel(): void;
  isRunning(): boolean;
};

/** null on web / Expo Go: everything then falls back to a plain navigation. */
export const Native: NativeTransitions | null =
  Platform.OS === 'android' || Platform.OS === 'ios'
    ? requireOptionalNativeModule<NativeTransitions>('DeliveryTransitions')
    : null;
