import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { fireEvent, render, screen } from '@testing-library/react';

jest.mock('app/shared/config/siteConfig', () => ({ loadSiteConfig: jest.fn().mockResolvedValue({ i18nLanguages: ['en'] }) }));

import { __resetLocale } from 'app/i18n/locale';
import { __applyCurrencyAnswer, __resetCurrency, CURRENCY_COOKIE, currentCurrency } from 'app/shared/util/currency';
import { money } from 'app/shared/util/money';

import CurrencySwitcher from './CurrencySwitcher';

const ALL = {
  country: 'DE',
  suggested: 'EUR',
  enabled: ['EUR', 'SEK', 'DKK', 'NOK', 'GBP'],
  rates: { SEK: 11.0125, DKK: 7.4641, NOK: 11.718, GBP: 0.8785 },
  asOf: '2026-11-13',
};

const at = (path: string, variant: 'header' | 'footer' = 'footer') =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <CurrencySwitcher variant={variant} />
    </MemoryRouter>
  );

beforeEach(async () => {
  await __resetLocale();
  __resetCurrency();
});

/** Absent-means-hidden, like the language switcher: one currency ⇒ no control at all. */
it('renders nothing while only EUR is on offer', () => {
  const { container } = at('/');
  expect(container.innerHTML).toBe('');
});

it('lists the enabled currencies and switches on click', () => {
  __applyCurrencyAnswer(ALL);
  at('/');
  for (const code of ['EUR', 'SEK', 'DKK', 'NOK', 'GBP']) expect(screen.getByText(code)).toBeTruthy();
  expect(screen.getByText('EUR').getAttribute('aria-pressed')).toBe('true');
  expect(screen.getByText('SEK').getAttribute('title')).toBe('Swedish Krona');

  fireEvent.click(screen.getByText('SEK'));
  expect(currentCurrency()).toBe('SEK');
  expect(document.cookie).toContain(`${CURRENCY_COOKIE}=SEK`);
  expect(screen.getByText('SEK').getAttribute('aria-pressed')).toBe('true');
  expect(money(10).replace(/\s/g, ' ')).toBe('110 kr');
});

it('shows only what the operator enabled', () => {
  __applyCurrencyAnswer({ ...ALL, enabled: ['EUR', 'SEK'], rates: { SEK: 11 } });
  at('/');
  expect(screen.getByText('SEK')).toBeTruthy();
  expect(screen.queryByText('GBP')).toBeNull();
});

/** Switching re-renders the page; a half-filled checkout must not be wiped by it. */
it('is absent on the checkout and pay pages', () => {
  __applyCurrencyAnswer(ALL);
  for (const path of ['/checkout', '/pay/12', '/pay/12/status']) {
    const { container, unmount } = at(path);
    expect(container.innerHTML).toBe('');
    unmount();
  }
  const { container } = at('/payments'); // the policy page is not the pay page
  expect(container.innerHTML).not.toBe('');
});

it('the header menu names the active currency', () => {
  __applyCurrencyAnswer({ ...ALL, suggested: 'GBP' });
  at('/', 'header');
  expect(screen.getByText('GBP')).toBeTruthy();
  expect(screen.getByText('Currency')).toBeTruthy();
});
