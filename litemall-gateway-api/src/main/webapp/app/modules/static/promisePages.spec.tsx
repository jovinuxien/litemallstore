import { readFileSync } from 'fs';
import { join } from 'path';

import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import Delivery from './Delivery';
import { FAQ_SECTIONS } from './faqData';
import Payments from './Payments';
import { AUTO_CONFIRM_DAYS, FREIGHT_FLAT, FREIGHT_FREE_MIN, UNPAID_MINUTES } from './shippingTerms';

/**
 * The pages behind the footer's "Tracked delivery" and "Secure payments" promises.
 *
 * These exist because the strip made claims with nothing behind them. The tests
 * that matter are therefore the NEGATIVE ones: a delivery page must not invent a
 * transit time, and a payments page must not name a payment method we have not
 * confirmed is switched on in the Stripe Dashboard.
 */
const at = (Page: React.FC): void => {
  render(
    <MemoryRouter>
      <Page />
    </MemoryRouter>
  );
};

describe('Shipping & delivery', () => {
  it('states the real flat rate and free-shipping threshold', () => {
    at(Delivery);
    expect(screen.getByText(new RegExp(FREIGHT_FLAT.replace('.', '\\.')))).toBeTruthy();
    expect(screen.getByText(new RegExp(FREIGHT_FREE_MIN.replace('.', '\\.')))).toBeTruthy();
  });

  it('states the auto-confirm and unpaid windows', () => {
    at(Delivery);
    expect(screen.getByText(new RegExp(`${AUTO_CONFIRM_DAYS} days after dispatch`))).toBeTruthy();
    expect(screen.getByText(new RegExp(`${UNPAID_MINUTES} minutes`))).toBeTruthy();
  });

  it('promises NO delivery window — we have measured none', () => {
    at(Delivery);
    // Any "3-5 days" / "5 to 10 business days" shape. The moment someone adds a
    // range, this fails and they have to point at the data behind it.
    expect(screen.queryByText(/\d+\s*(-|–|to)\s*\d+\s*(business\s*)?(working\s*)?days/i)).toBeNull();
    expect(screen.queryByText(/next.day|within \d+ days/i)).toBeNull();
  });

  it('describes the EU label as a stock reading, not a delivery guarantee', () => {
    at(Delivery);
    expect(screen.getByText(/most recent stock check/i)).toBeTruthy();
    // "not necessarily slower" — eu_flag=0 means "not known", never "not in the EU".
    expect(screen.getByText(/not necessarily slower/i)).toBeTruthy();
  });
});

describe('How payments work', () => {
  it('explains that card details never reach us', () => {
    at(Payments);
    expect(screen.getByText(/never reach Trovemo/i)).toBeTruthy();
  });

  it('states the server-side amount check, which is the real guarantee', () => {
    at(Payments);
    expect(screen.getByText(/matches the\s+order to the cent/i)).toBeTruthy();
  });

  it('names NO payment method we have not confirmed is enabled', () => {
    at(Payments);
    // The adapter enables Stripe's automatic payment methods, so what appears at
    // checkout is a Dashboard setting this codebase cannot read. Card and balance
    // are certain; these are not.
    ['Klarna', 'iDEAL', 'SEPA', 'Bancontact', 'MobilePay', 'PayPal', 'Apple Pay', 'Google Pay'].forEach(method => {
      expect(screen.queryByText(new RegExp(method, 'i'))).toBeNull();
    });
  });

  it('does not claim a failed payment can never be a charge', () => {
    at(Payments);
    // A payment CAN succeed at Stripe and still fail our verification, so the
    // absolute version of this reassurance would be false.
    expect(screen.queryByText(/never a charge|cannot be charged/i)).toBeNull();
    expect(screen.getByText(/declined is not charged/i)).toBeTruthy();
  });
});

describe('help centre picks the new pages up', () => {
  const entries = FAQ_SECTIONS.flatMap(s => s.entries);

  it('answers what shipping costs, from the same constants as the page', () => {
    const entry = entries.find(e => e.id === 'shipping-cost');
    expect(entry).toBeTruthy();
    expect(entry?.a).toContain(FREIGHT_FLAT);
    expect(entry?.a).toContain(FREIGHT_FREE_MIN);
  });

  it('answers what the EU stock label means', () => {
    expect(entries.find(e => e.id === 'eu-stock')?.a).toMatch(/stock reading rather than a delivery guarantee/i);
  });

  it('answers whether cards are safe, and links to the payments page', () => {
    const entry = entries.find(e => e.id === 'card-safety');
    expect(entry?.links?.some(l => l.to === '/payments')).toBe(true);
  });

  it('keeps every answer pointing at a route the app actually declares', () => {
    // Read the router rather than keep a second list here: a hand-maintained
    // allowlist would drift from App.tsx and start failing for the wrong reason.
    const app = readFileSync(join(__dirname, '..', '..', 'App.tsx'), 'utf8');
    const declared = new Set((app.match(/path='[^']+'/g) ?? []).map(m => '/' + m.slice(6, -1).replace(/^\//, '')));
    entries
      .flatMap(e => e.links ?? [])
      .forEach(l => {
        const top = '/' + l.to.split('/')[1];
        expect(declared.has(l.to) || declared.has(top)).toBe(true);
      });
  });
});
