import React from 'react';
import { act, render } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';

import store from 'app/config/store';
import { __resetLocale, applyEnabledLanguages, setLocale } from 'app/i18n/locale';

import ProductCard, { fmtRemaining } from './ProductCard';

jest.mock('app/shared/config/siteConfig', () => ({ loadSiteConfig: jest.fn().mockResolvedValue({ i18nLanguages: ['en'] }) }));

/**
 * i18n batch 2 — the product card is the one component on every grid, so it is
 * where a half-translated site is most visible. Two properties:
 *  1. every badge/CTA the card renders comes from the catalogue, so Swedish shows
 *     Swedish (a key missing from sv/ would fall back to English silently — the
 *     parity spec guards that at the file level, this guards the wiring);
 *  2. a MOUNTED card follows a language switch without being remounted — the
 *     hook subscription, not a route remount, is what re-renders it.
 */
const renderCard = (product: any) =>
  render(
    <Provider store={store}>
      <MemoryRouter>
        <ProductCard product={product} />
      </MemoryRouter>
    </Provider>
  );

const base = { id: 10008302, name: 'Ceramic table lamp', picUrl: '/img.jpg', retailPrice: 19.99, coupon_flag: 1, eu_flag: 1 };

beforeEach(async () => {
  await __resetLocale();
  await applyEnabledLanguages(['sv', 'da']);
});

afterAll(async () => {
  await __resetLocale();
});

it('renders the card chrome in Swedish once sv is the locale', async () => {
  await setLocale('sv');
  const { container } = renderCard(base);
  expect(container.querySelector('.lm-card__cart')!.textContent).toBe('Lägg i varukorgen');
  expect(container.querySelector('.lm-card__coupon')!.textContent).toBe('Rabattkod');
  expect(container.querySelector('.lm-card__eustock')!.textContent).toBe('EU-lager');
  expect(container.querySelector('.lm-card__eustock')!.getAttribute('title')).toBe('Fanns i ett EU-lager vid vår senaste lagerkontroll.');
});

it('re-renders a mounted card when the language switches (no remount needed)', async () => {
  const { container } = renderCard(base);
  const button = container.querySelector('.lm-card__cart')!;
  expect(button.textContent).toBe('Add to cart');
  await act(async () => {
    await setLocale('da');
  });
  // Same DOM node — the card was updated in place, not torn down.
  expect(container.querySelector('.lm-card__cart')).toBe(button);
  expect(button.textContent).toBe('Læg i kurv');
});

it('keeps the countdown units per locale and never localises the digits', () => {
  const now = 0;
  const twoDays = 2 * 24 * 60 * 60 * 1000 + 4 * 60 * 60 * 1000;
  expect(fmtRemaining(twoDays, now)).toBe('2d 4h');
  expect(fmtRemaining(twoDays, now, { d: 'd', h: 't', m: 'min' })).toBe('2d 4t');
  expect(fmtRemaining(8 * 60 * 1000, now, { d: 'd', h: 'h', m: 'min' })).toBe('8min');
  expect(fmtRemaining(-1, now)).toBeNull();
});
