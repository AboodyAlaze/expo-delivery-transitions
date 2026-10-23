/**
 * The JavaScript half of the transition, with a fake native module: the ORDER of the calls, the fallbacks and the
 * safety nets (a transition must never lose a navigation or block the app).
 */
const calls: string[] = [];
const fake: Record<string, jest.Mock> = {};

function resetFake(overrides: Record<string, unknown> = {}) {
  calls.length = 0;
  const mk = (name: string, impl?: (...a: unknown[]) => unknown) =>
    jest.fn((...a: unknown[]) => {
      calls.push(name);
      return impl ? impl(...a) : undefined;
    });
  fake.begin = mk('begin', () => Promise.resolve());
  fake.preroll = mk('preroll');
  fake.screenReady = mk('screenReady', () => Promise.resolve(true));
  fake.play = mk('play', () => Promise.resolve());
  fake.refresh = mk('refresh');
  fake.skip = mk('skip');
  fake.cancel = mk('cancel');
  for (const [k, v] of Object.entries(overrides)) {
    if (v === undefined) delete fake[k];
    else fake[k] = v as jest.Mock;
  }
}

jest.mock('../NativeTransitions', () => ({
  get Native() {
    return fake;
  },
}));

// eslint-disable-next-line import/first
import { runTransition, isTransitionRunning } from '../runTransition';

beforeEach(() => resetFake());

describe('runTransition (with a native module)', () => {
  it('freezes, starts the vehicle, navigates, waits for the REAL new screen, then plays', async () => {
    const navigate = jest.fn(() => calls.push('navigate'));
    await runTransition({ name: 'deliveryBike' }, navigate);
    expect(calls).toEqual(['begin', 'preroll', 'navigate', 'screenReady', 'play']);
    expect(isTransitionRunning()).toBe(false);
  });

  it('plays with a solid black background by default and passes the options as JSON', async () => {
    await runTransition({ name: 'deliveryCar', durationMs: 900 }, () => {});
    const json = JSON.parse(fake.play.mock.calls[0][0] as string);
    expect(json.name).toBe('deliveryCar');
    expect(json.durationMs).toBe(900);
    expect(json.background >>> 0).toBe(0xff000000);
  });

  it('falls back to a plain navigation when the screen cannot be frozen', async () => {
    resetFake({ begin: jest.fn(() => Promise.reject(new Error('no activity'))) });
    const navigate = jest.fn();
    await runTransition({ name: 'deliveryBike' }, navigate);
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(fake.play).not.toHaveBeenCalled();
    expect(isTransitionRunning()).toBe(false);
  });

  it('removes the overlay and rethrows when navigate() itself fails', async () => {
    const boom = new Error('bad route');
    await expect(
      runTransition({ name: 'deliveryBike' }, () => {
        throw boom;
      })
    ).rejects.toBe(boom);
    expect(fake.cancel).toHaveBeenCalled();
    expect(isTransitionRunning()).toBe(false);
  });

  it('keeps the new screen and removes the overlay when the animation fails', async () => {
    resetFake({ play: jest.fn(() => Promise.reject(new Error('draw failed'))) });
    const navigate = jest.fn();
    await runTransition({ name: 'deliveryBike' }, navigate);
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(fake.cancel).toHaveBeenCalled();
    expect(isTransitionRunning()).toBe(false);
  });

  it('ignores a second tap while a transition is running (no double navigation)', async () => {
    let release: () => void = () => {};
    resetFake({
      play: jest.fn(() => new Promise<void>((resolve) => (release = resolve))),
    });
    const first = jest.fn();
    const second = jest.fn();
    const p = runTransition({ name: 'deliveryBike' }, first);
    await new Promise((r) => setTimeout(r, 20));
    await runTransition({ name: 'deliveryBike' }, second);
    expect(second).not.toHaveBeenCalled();
    release();
    await p;
    expect(first).toHaveBeenCalledTimes(1);
  });

  it('does NOT animate a stale picture when the new screen was never detected', async () => {
    resetFake({ screenReady: jest.fn(() => Promise.resolve(false)) });
    const navigate = jest.fn();
    await runTransition({ name: 'deliveryBike' }, navigate);
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(fake.play).not.toHaveBeenCalled();
    expect(fake.cancel).toHaveBeenCalled();
    expect(isTransitionRunning()).toBe(false);
  });

  it('accepts an older native build whose screenReady answers nothing', async () => {
    resetFake({ screenReady: jest.fn(() => Promise.resolve()) });
    await runTransition({ name: 'deliveryBike' }, () => {});
    expect(fake.play).toHaveBeenCalledTimes(1);
  });

  it('sends the tapped card rectangle to the native side (cardExpand)', async () => {
    await runTransition(
      { name: 'cardExpand', originRect: { x: 20, y: 120, width: 300, height: 180 } },
      () => {}
    );
    const json = JSON.parse(fake.play.mock.calls[0][0] as string);
    expect(json.name).toBe('cardExpand');
    expect(json.originRect).toEqual({ x: 20, y: 120, width: 300, height: 180 });
  });

  it('single-bag delivery starts the vehicle at once, like the other delivery modes', async () => {
    await runTransition({ name: 'deliveryBikeBag' }, () => calls.push('navigate'));
    expect(calls).toEqual(['begin', 'preroll', 'navigate', 'screenReady', 'play']);
    const json = JSON.parse(fake.play.mock.calls[0][0] as string);
    expect(json.name).toBe('deliveryBikeBag');
  });

  it('waits long for a forward screen (3 s) but never long when going back (0.7 s)', async () => {
    await runTransition({ name: 'deliveryCarBag' }, () => {});
    expect(fake.screenReady.mock.calls[0][0]).toBe(3000);
    resetFake();
    await runTransition({ name: 'dismiss' }, () => {});
    expect(fake.screenReady.mock.calls[0][0]).toBe(700);
  });

  it('works with an older native build that has no screenReady / preroll', async () => {
    resetFake({ screenReady: undefined, preroll: undefined });
    const navigate = jest.fn(() => calls.push('navigate'));
    await runTransition({ name: 'deliveryBike', settleMs: 1 }, navigate);
    expect(calls).toEqual(['begin', 'navigate', 'play']);
  });
});
