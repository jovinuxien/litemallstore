import type { Stripe } from '@stripe/stripe-js';
// '/pure', NOT the main entry: importing '@stripe/stripe-js' directly INJECTS js.stripe.com
// as an import side effect, so merely bundling it would hit Stripe (and its
// m.stripe.network fingerprinting endpoints) on page load — with no publishable key, no
// card selected, and no consent. '/pure' defers all of that until loadStripe() is called.
// Verified: with this import the unconfigured checkout makes ZERO requests to stripe.com.
import { loadStripe } from '@stripe/stripe-js/pure';

import { CURRENCY } from 'app/shared/util/money';

/**
 * Stripe.js is loaded once per page, lazily, and ONLY when a publishable key exists —
 * an unconfigured deployment must make no Stripe request at all, the same discipline the
 * Matomo tracker follows. Cached because loadStripe() must not run per render. Shared by
 * the checkout and the standalone pay page so the two can never load it twice.
 */
let stripeJs: Promise<Stripe | null> | null = null;
export const stripePromiseFor = (publishableKey: string): Promise<Stripe | null> => {
  stripeJs = stripeJs ?? loadStripe(publishableKey);
  return stripeJs;
};

/**
 * The currency a deferred-intent Payment Element is mounted with BEFORE an order (and so
 * an intent) exists. It is the store's one display currency, lower-cased the way Stripe
 * writes it — the same constant every price on the page is rendered from, so the form
 * cannot offer payment in a currency the page is not showing.
 *
 * This decides which payment methods Stripe presents: the euro-only methods (SEPA debit,
 * iDEAL, Bancontact, Klarna in the euro area) appear only for `eur`. It was a hardcoded
 * 'usd' left over from before the Wave-24 EUR flip, while the order service mints EUR
 * intents — Elements and intent disagreed. The server remains the authority: it asserts
 * the intent's currency at pay time and rejects a mismatch.
 */
export const STRIPE_ELEMENTS_CURRENCY = CURRENCY.toLowerCase();
