import { useEffect, useState } from 'react';

import { IPageView } from 'app/shared/api';
import { loadSeason } from './season';

/**
 * Wave 27 — the running season for the header strip, the "☰ All" drawer and
 * the home rail. All three read THIS, so they cannot disagree about whether a
 * season exists or what it is called.
 *
 * Returns null until the answer is known, so a season link is never rendered
 * speculatively (and, unlike the Wave-26 content probes, never rendered at all
 * on failure — `season.ts` explains why fail-open is not available here).
 *
 * Deferred to browser idle, matching `useContentAvailability`: deciding what
 * the nav says must not compete with the page the shopper actually asked for.
 */
export const useSeason = (): IPageView | null => {
  const [season, setSeason] = useState<IPageView | null>(null);

  useEffect(() => {
    let cancelled = false;
    const w = window as Window & {
      requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number;
      cancelIdleCallback?: (handle: number) => void;
    };
    const run = () => {
      loadSeason().then(page => {
        if (!cancelled) setSeason(page);
      });
    };
    if (typeof w.requestIdleCallback === 'function') {
      const handle = w.requestIdleCallback(run, { timeout: 2000 });
      return () => {
        cancelled = true;
        w.cancelIdleCallback?.(handle);
      };
    }
    const timer = setTimeout(run, 0);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, []);

  return season;
};

export default useSeason;
