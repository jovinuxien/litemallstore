import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import NotFound from './NotFound';

/**
 * Not a rare page: the Wave-26 narrowing cut the catalogue from ~17,000 on-sale
 * goods to ~3,600, and retired product URLs stay in Google's index for weeks.
 * It used to be an unstyled string with nothing to click.
 */
const at = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <NotFound />
    </MemoryRouter>
  );

it('names the address that failed, so the visitor knows what we understood', () => {
  at('/product/10000001-a-retired-thing');
  expect(screen.getByText('/product/10000001-a-retired-thing')).toBeTruthy();
});

it('offers a search and routes onward instead of dead-ending', () => {
  at('/nope');
  expect(screen.getByLabelText('Search products')).toBeTruthy();
  expect(screen.getByText('Home page').getAttribute('href')).toBe('/');
  expect(screen.getByText('All products').getAttribute('href')).toBe('/search');
  expect(screen.getByText('Today’s Deals').getAttribute('href')).toBe('/deals');
});

it('says the product may simply be off sale — the common cause after a narrowing', () => {
  at('/product/999');
  expect(document.body.textContent).toMatch(/no longer on sale/i);
});

it('does not guess what the visitor wanted from the URL', () => {
  at('/category/1036143/womens-clothing');
  // No seeded query: a confident wrong guess is worse than an honest miss.
  expect((screen.getByLabelText('Search products') as HTMLInputElement).value).toBe('');
});
