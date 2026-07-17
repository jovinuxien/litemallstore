/**
 * Cookie-consent state (Wave-7 Task C).
 *
 * A tiny observable store rather than Redux: consent has to be readable by
 * `matomo.ts`, which is plain module code deliberately kept outside React (the
 * tracker must work regardless of render timing). Redux would invert that.
 *
 * <p>Storage is `localStorage`, not the `sessionStorage` used for the affiliate
 * `?invite=` stash — a consent decision that evaporated with the tab would mean
 * re-asking on every visit, which is not a real choice. Storing the decision
 * itself needs no consent: it is strictly necessary (it is what remembers a
 * refusal), and it is not a cookie.
 *
 * <p>`reason` exists so the preferences page can be honest about WHY tracking is
 * off — "you declined" and "this deployment has no analytics configured" and
 * "your browser sends Do Not Track" are three different statements, and the first
 * is the only one the user can change here.
 */

export type ConsentChoice = 'granted' | 'denied';

/** Why tracking is or isn't available, independent of the user's choice. */
export type TrackingReason =
  /** Site config has not resolved yet. */
  | 'pending'
  /** No Matomo configured on this deployment ⇒ nothing to consent to. */
  | 'unconfigured'
  /** Browser sent Do-Not-Track ⇒ a hard opt-out we honour without asking. */
  | 'dnt'
  /** Matomo is configured and DNT is absent ⇒ the user's choice decides. */
  | 'available';

export interface ConsentState {
  reason: TrackingReason;
  choice: ConsentChoice | null;
}

const STORAGE_KEY = 'cookieConsent';

/** Private-mode Safari and friends throw on storage access; never let that break the page. */
const readStored = (): ConsentChoice | null => {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return v === 'granted' || v === 'denied' ? v : null;
  } catch {
    return null;
  }
};

const writeStored = (choice: ConsentChoice | null): void => {
  try {
    if (choice) localStorage.setItem(STORAGE_KEY, choice);
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Non-persistent consent is still honoured for this page's lifetime.
  }
};

let state: ConsentState = { reason: 'pending', choice: readStored() };

const listeners = new Set<() => void>();
const emit = (): void => listeners.forEach(l => l());

export const subscribeConsent = (listener: () => void): (() => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

/** Stable snapshot — useSyncExternalStore compares by identity, so only replace on real change. */
export const consentSnapshot = (): ConsentState => state;

/** Called by matomo.ts once site-config resolves. */
export const setTrackingReason = (reason: TrackingReason): void => {
  if (state.reason === reason) return;
  state = { ...state, reason };
  emit();
};

export const chooseConsent = (choice: ConsentChoice): void => {
  if (state.choice === choice) return;
  writeStored(choice);
  state = { ...state, choice };
  emit();
};

/**
 * Withdraw a previous decision and return to the un-asked state — the banner comes
 * back. Distinct from `chooseConsent('denied')`, which is an active refusal.
 */
export const resetConsent = (): void => {
  writeStored(null);
  state = { ...state, choice: null };
  emit();
};
