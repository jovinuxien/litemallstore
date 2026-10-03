const mockLoadStripe = jest.fn((_key: string) => Promise.resolve(null));
jest.mock('@stripe/stripe-js/pure', () => ({ loadStripe: (key: string) => mockLoadStripe(key) }));

import { CURRENCY } from 'app/shared/util/money';

import { STRIPE_ELEMENTS_CURRENCY, stripePromiseFor } from './stripeLoader';

describe('stripeLoader', () => {
  it('mounts the card form in the currency the store displays and charges', () => {
    // The checkout carried a hardcoded 'usd' here for two months after the store moved to
    // EUR: Stripe then picks payment methods for dollars while the server mints euro
    // intents. Tied to the display currency so the two cannot part again.
    expect(STRIPE_ELEMENTS_CURRENCY).toBe(CURRENCY.toLowerCase());
    expect(STRIPE_ELEMENTS_CURRENCY).toBe('eur');
  });

  it('loads Stripe.js once per page, and only when asked', () => {
    expect(mockLoadStripe).not.toHaveBeenCalled(); // importing the module contacts nobody
    const first = stripePromiseFor('pk_test_1');
    const second = stripePromiseFor('pk_test_1');
    expect(second).toBe(first);
    expect(mockLoadStripe).toHaveBeenCalledTimes(1);
  });
});
