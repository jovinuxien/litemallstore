import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import CustomerService from './CustomerService';
import { SUPPORT_EMAIL, SUPPORT_HOURS } from './faqData';

/**
 * The footer's customer-promise strip and /service each stated support hours,
 * from their own hardcoded string, and disagreed: the footer promised
 * "Customer care, every day" while /service said Mon–Fri. Both now read
 * SUPPORT_HOURS, which is the same drift guard faqData already gives /help and
 * /service for the answers themselves.
 *
 * Layout is not rendered here — it needs a redux store, the site-config store
 * and matchMedia. Layout.spec.tsx owns that setup; what this file pins is the
 * constant's shape and that /service renders it, so a future edit that inlines
 * a literal on either side fails somewhere.
 */
// /service renders SocialLinks, which loads /auth/site-config through fetch —
// absent in jsdom. The icons are irrelevant here; an empty config just hides
// them.
beforeAll(() => {
  (global as unknown as { fetch: unknown }).fetch = jest.fn(() =>
    Promise.resolve({ json: () => Promise.resolve({ data: {} }) })
  );
});

describe('support hours are stated once', () => {
  it('names weekdays and a time range, never a daily promise', () => {
    expect(SUPPORT_HOURS).toMatch(/Mon.*Fri/);
    expect(SUPPORT_HOURS).toMatch(/\d{1,2}:\d{2}/);
    expect(SUPPORT_HOURS.toLowerCase()).not.toContain('every day');
  });

  it('/service renders the shared hours against the email channel', () => {
    render(
      <MemoryRouter>
        <CustomerService />
      </MemoryRouter>
    );
    expect(screen.getByText(new RegExp(SUPPORT_HOURS.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))).toBeTruthy();
    expect(screen.getByText(SUPPORT_EMAIL)).toBeTruthy();
  });

  it('offers no channel it cannot answer — the phantom "Online support" row is gone', () => {
    render(
      <MemoryRouter>
        <CustomerService />
      </MemoryRouter>
    );
    // The row carried hours and a headset icon but no chat, no phone and no
    // link: an advertised channel with no way in.
    expect(screen.queryByText(/online support/i)).toBeNull();
    expect(screen.getByText(/email support/i)).toBeTruthy();
  });
});
