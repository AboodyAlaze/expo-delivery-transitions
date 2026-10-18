import { runTransition } from '../runTransition';

describe('runTransition', () => {
  it('just navigates when the native module is not available (iOS / web / Expo Go)', async () => {
    const navigate = jest.fn();
    await runTransition({ name: 'deliveryBike' }, navigate);
    expect(navigate).toHaveBeenCalledTimes(1);
  });
});
