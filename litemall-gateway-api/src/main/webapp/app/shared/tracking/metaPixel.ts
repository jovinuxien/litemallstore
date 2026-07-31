/**
 * Meta Pixel bootstrap (Wave 15) — the SAME consent contract as matomo.ts:
 * driven by `GET /auth/site-config` (`metaPixelId` null ⇒ permanently
 * unavailable this page load, byte-identical behavior to the pre-Wave-15
 * bundle), Do-Not-Track honored as a hard disable that outranks any stored
 * consent, and NOTHING loads until the visitor affirmatively accepts — the
 * fbevents.js script tag itself is withheld, because loading it IS the network
 * call we promise not to make.
 *
 * <p>Withdrawal stops all further calls; fbevents.js has no unload, so the
 * already-loaded script simply goes silent for the rest of the page lifetime.
 *
 * <p>Funnel events are NOT buffered across the consent decision (unlike page
 * views in matomo.ts): replaying a pre-consent add-to-cart after acceptance
 * would claim it happened now. Events fired while undecided are dropped.
 *
 * <p>If the pixel is configured, tracking is "available" for the cookie banner
 * even when Matomo is not — `initMetaPixel` upgrades the shared consent reason
 * (never downgrades it; matomo.ts owns the unconfigured/dnt verdicts it makes).
 */

import { ConsentChoice, consentSnapshot, setTrackingReason } from 'app/shared/tracking/consent';

type PixelState = 'pending' | 'unavailable' | 'awaiting-consent' | 'granted' | 'denied';

type Fbq = ((...args: unknown[]) => void) & {
  callMethod?: (...args: unknown[]) => void;
  queue: unknown[][];
  push: unknown;
  loaded: boolean;
  version: string;
};

declare global {
  interface Window {
    fbq?: Fbq;
    _fbq?: Fbq;
  }
}

let state: PixelState = 'pending';
let pixelId: string | null = null;
let scriptInjected = false;
// Events fired before site-config resolves — same contract as matomo.ts's
// pendingCommands: flushed only when a STORED grant carries through init,
// dropped on any other outcome.
let pendingEvents: { event: string; payload?: Record<string, unknown> }[] = [];

const doNotTrack = (): boolean => {
  const dnt = navigator.doNotTrack ?? (window as unknown as { doNotTrack?: string }).doNotTrack;
  return dnt === '1' || dnt === 'yes';
};

/** Visible for tests/debugging only. */
export const metaPixelState = (): PixelState => state;

/** Decide once whether the pixel is even possible. Idempotent. */
export const initMetaPixel = (cfg: { metaPixelId?: string | null }): void => {
  if (state !== 'pending') return;
  if (!cfg.metaPixelId || doNotTrack()) {
    state = 'unavailable';
    pendingEvents = [];
    return;
  }
  pixelId = cfg.metaPixelId;
  state = 'awaiting-consent';
  // There is now something to consent to, even if Matomo said "unconfigured".
  setTrackingReason('available');
  applyPixelConsent(consentSnapshot().choice);
};

/** Apply the visitor's decision. No-op while unconfigured or under DNT. */
export const applyPixelConsent = (choice: ConsentChoice | null): void => {
  if (state === 'pending' || state === 'unavailable') return;
  if (choice === 'granted') {
    grant();
  } else {
    state = choice === 'denied' ? 'denied' : 'awaiting-consent';
    pendingEvents = []; // no decision (or refusal) ⇒ pre-decision events are gone
  }
};

const grant = (): void => {
  if (state === 'granted') return;
  state = 'granted';
  if (!scriptInjected) {
    scriptInjected = true;
    if (!window.fbq) {
      // Standard fbevents stub: queues calls until the real script arrives.
      const fbq = ((...args: unknown[]) => {
        if (fbq.callMethod) fbq.callMethod(...args);
        else fbq.queue.push(args);
      }) as Fbq;
      fbq.queue = [];
      fbq.push = fbq;
      fbq.loaded = true;
      fbq.version = '2.0';
      window.fbq = fbq;
      window._fbq = fbq;
      const script = document.createElement('script');
      script.async = true;
      script.src = 'https://connect.facebook.net/en_US/fbevents.js';
      document.head.appendChild(script);
    }
    window.fbq!('init', pixelId!);
    window.fbq!('track', 'PageView');
  }
  const queued = pendingEvents;
  pendingEvents = [];
  queued.forEach(e => window.fbq!('track', e.event, e.payload));
};

/** SPA route-change page view. The initial view rides `grant()`. */
export const pixelPageView = (): void => {
  if (state !== 'granted') return;
  window.fbq?.('track', 'PageView');
};

/**
 * Track a standard event with its payload. Granted ⇒ sent; pending ⇒ buffered
 * (flushed only by a stored grant); otherwise dropped.
 */
export const pixelTrack = (event: string, payload?: Record<string, unknown>): void => {
  if (state === 'granted') window.fbq?.('track', event, payload);
  else if (state === 'pending') pendingEvents.push({ event, payload });
};
