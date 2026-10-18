import { startReadinessRun } from '../transitionReady';

describe('startReadinessRun', () => {
  it('does not wait when no screen registered', async () => {
    const run = startReadinessRun();
    await expect(run.waitUntilReady(50)).resolves.toBe(true);
    run.end();
  });
});
