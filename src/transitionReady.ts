import { useEffect, useRef } from 'react';

type Run = { pending: Set<symbol>; wake: (() => void) | null };

/** The transition that is waiting right now (there is at most one). */
let activeRun: Run | null = null;

/** Used by `runTransition`: opens a run BEFORE navigating, so screens that mount meanwhile can register. */
export function startReadinessRun() {
  const run: Run = { pending: new Set(), wake: null };
  activeRun = run;
  return {
    /** Resolves `true` when every registered screen is ready, `false` when the timeout wins. */
    waitUntilReady(timeoutMs: number): Promise<boolean> {
      if (run.pending.size === 0) return Promise.resolve(true);
      return new Promise<boolean>((resolve) => {
        const timer = setTimeout(() => {
          run.wake = null;
          resolve(false);
        }, timeoutMs);
        run.wake = () => {
          if (run.pending.size === 0) {
            clearTimeout(timer);
            run.wake = null;
            resolve(true);
          }
        };
      });
    },
    end() {
      if (activeRun === run) activeRun = null;
      run.wake = null;
    },
  };
}

/**
 * Call it in the screen you navigate TO, with `true` once the screen shows what the user should see
 * (data loaded, header drawn...). The transition photographs the new screen only then, instead of a half-loaded
 * skeleton. A screen that never calls it is never waited for, and the wait is capped by `readyTimeoutMs`.
 *
 *     const { data, isLoading } = useFetch(...);
 *     useTransitionReady(!isLoading);
 */
export function useTransitionReady(ready: boolean = true) {
  const id = useRef<symbol>(Symbol('transition-ready'));
  const run = useRef<Run | null>(null);

  useEffect(() => {
    const token = id.current;
    if (activeRun) {
      run.current = activeRun;
      activeRun.pending.add(token);
    }
    return () => {
      const r = run.current;
      if (r) {
        r.pending.delete(token);
        r.wake?.();
      }
    };
  }, []);

  useEffect(() => {
    const r = run.current;
    if (ready && r) {
      r.pending.delete(id.current);
      r.wake?.();
    }
  }, [ready]);
}
