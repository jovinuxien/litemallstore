import { describeError } from 'app/i18n/errors';

/**
 * Checkout contact email — pure helpers (plan-order-email-guarantee-2026-11-12, step 1).
 *
 * The order-paid, shipped and delivered mails all go to the ACCOUNT's email, and the
 * order service refuses a submit from an account that has none. So the checkout must
 * not place an order unless the email it collected really landed on the account.
 */

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const EMAIL_MAX = 127;

/**
 * The edge's own rule (`AccountService.normalizeEmail`: one "@", a dot after it, no
 * whitespace, at most 127 characters). Mirrored so an address the form accepts is one
 * the server will store — the looser check used before let "a@b@c.d" through the form
 * and then lost it at the save.
 */
export const isValidCheckoutEmail = (raw: string | null | undefined): boolean => {
  const email = (raw ?? '').trim();
  return email.length <= EMAIL_MAX && EMAIL.test(email);
};

export type EmailSaveOutcome = { ok: true } | { ok: false; reason: string | null };

/**
 * What a `/auth/profile {email}` answer means. `reason` is the server's own wording
 * (localised by errno when we have a translation) or null when it gave none — the
 * caller then shows its generic line.
 */
export const emailSaveOutcome = (env: { errno?: number; errmsg?: string | null } | null | undefined): EmailSaveOutcome => {
  if (env != null && env.errno === 0) return { ok: true };
  const hasWords = env != null && (env.errno != null || !!(env.errmsg ?? '').trim());
  return { ok: false, reason: hasWords ? describeError(env.errno, env.errmsg) : null };
};

/**
 * True when the order service refused the submit because the account has no email
 * ("Add an email address to place this order — …"). The message itself is shown
 * verbatim; this only decides whether to reveal the email field so the customer can
 * act on it. A miss is harmless: the message still shows.
 */
export const isEmailRequiredRefusal = (message: string | null | undefined): boolean =>
  /\bemail address\b/i.test(message ?? '');
