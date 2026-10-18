import { clipTargets, fillTargets } from '../DeliveryTarget';

const r = (y: number, height: number) => ({ x: 0, y, width: 300, height });

describe('fillTargets', () => {
  it('leaves 0 targets alone (native falls back to bands) and 5 as they are', () => {
    expect(fillTargets([])).toEqual([]);
    const five = [r(0, 10), r(10, 10), r(20, 10), r(30, 10), r(40, 10)];
    expect(fillTargets(five)).toEqual(five);
  });

  it('splits the tallest target until there are 5, keeping top-to-bottom order', () => {
    const out = fillTargets([r(0, 50), r(60, 80), r(150, 140), r(300, 140)]);
    expect(out).toHaveLength(5);
    const ys = out.map((o) => o.y);
    expect(ys).toEqual([...ys].sort((a, b) => a - b));
    expect(out[2]).toEqual(r(150, 70));
    expect(out[3]).toEqual(r(220, 70));
  });

  it('works with a single target', () => {
    const out = fillTargets([r(100, 400)]);
    expect(out).toHaveLength(5);
    expect(out.reduce((sum, o) => sum + o.height, 0)).toBe(400);
  });
});

describe('clipTargets', () => {
  it('keeps the on-screen part of a long list and drops what is off screen or tiny', () => {
    const out = clipTargets([r(300, 2000), r(-40, 80), r(900, 10), r(2500, 100)], 800);
    expect(out).toEqual([r(0, 40), r(300, 500)]);
  });
});
