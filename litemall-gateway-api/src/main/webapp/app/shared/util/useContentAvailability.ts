import { useEffect, useState } from 'react';

import { ContentSurface, surfaceAvailable } from './contentAvailability';

/** Run when the browser has nothing better to do; plain timeout where idle callbacks don't exist. */
const whenIdle = (fn: () => void): (() => void) => {
  const w = window as Window & {
    requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number;
    cancelIdleCallback?: (handle: number) => void;
  };
  if (typeof w.requestIdleCallback === 'function') {
    const handle = w.requestIdleCallback(fn, { timeout: 2000 });
    return () => w.cancelIdleCallback?.(handle);
  }
  const timer = setTimeout(fn, 0);
  return () => clearTimeout(timer);
};

/**
 * Wave 26 — nav gate for a storefront section (see `contentAvailability.ts`).
 *
 * Returns false until the probe has confirmed the section has content, so a
 * link to an empty page is never rendered even for a moment; a failed probe
 * resolves true, because losing the store's navigation to a hiccup is worse
 * than one click that lands on an honest empty state.
 *
 * The probe is deferred to browser idle: deciding whether to show a nav link
 * must never compete with the page the shopper actually asked for. It then runs
 * once per session for the whole SPA, however many components ask.
 */
export const useContentAvailability = (surface: ContentSurface): boolean => {
  const [available, setAvailable] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const cancelIdle = whenIdle(() => {
      surfaceAvailable(surface).then(ok => {
        if (!cancelled) setAvailable(ok);
      });
    });
    return () => {
      cancelled = true;
      cancelIdle();
    };
  }, [surface]);

  return available;
};

export default useContentAvailability;
