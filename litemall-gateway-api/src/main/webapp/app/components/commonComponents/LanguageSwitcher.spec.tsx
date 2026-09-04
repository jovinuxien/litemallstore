import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

jest.mock('app/shared/config/siteConfig', () => ({ loadSiteConfig: jest.fn().mockResolvedValue({ i18nLanguages: ['en'] }) }));

import { t } from 'app/i18n';
import { __resetLocale, applyEnabledLanguages, currentLocale, readLangCookie } from 'app/i18n/locale';

import LanguageSwitcher from './LanguageSwitcher';

/**
 * The switcher is HIDDEN until more than one language is enabled — the same
 * absent-means-hidden rule as every env-gated storefront feature. Names are endonyms.
 */
beforeEach(async () => {
  await __resetLocale();
});

it('renders nothing while only English is enabled', () => {
  const { container } = render(<LanguageSwitcher variant='footer' />);
  expect(container.innerHTML).toBe('');
});

it('lists the enabled languages by their own names and switches on click', async () => {
  await applyEnabledLanguages(['sv', 'da']);
  render(<LanguageSwitcher variant='footer' />);
  expect(screen.getByText('English')).toBeTruthy();
  expect(screen.getByText('Svenska')).toBeTruthy();
  expect(screen.getByText('Dansk')).toBeTruthy();
  expect(screen.getByText('English').getAttribute('aria-pressed')).toBe('true');

  fireEvent.click(screen.getByText('Dansk'));
  await waitFor(() => expect(currentLocale()).toBe('da'));
  expect(readLangCookie()).toBe('da');
  expect(document.documentElement.lang).toBe('da');
  expect(t('header.cart')).toBe('Kurv');
  await waitFor(() => expect(screen.getByText('Dansk').getAttribute('aria-pressed')).toBe('true'));
  // The group label follows the switch too.
  expect(screen.getByRole('group').getAttribute('aria-label')).toBe('Sprog');
});

it('shows only what the operator enabled', async () => {
  await applyEnabledLanguages(['sv']);
  render(<LanguageSwitcher variant='footer' />);
  expect(screen.getByText('Svenska')).toBeTruthy();
  expect(screen.queryByText('Dansk')).toBeNull();
});
