import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';

import store from 'app/config/store';
import { IGood } from 'app/shared/model/product/product.model';

import ProductCard from './ProductCard';

/**
 * Wave-27 eu_flag on the results grid.
 *
 * The flag is a MEASUREMENT, not a promise: 1 means the last inventory probe
 * found EU-warehouse stock, and 0 lumps "probed, none" together with "never
 * probed". So a positive renders and a negative renders NOTHING — there is no
 * "ships from China" counterpart to be got wrong.
 */
const card = (product: Partial<IGood> & Record<string, unknown>): void => {
  render(
    <Provider store={store}>
      <MemoryRouter>
        <ProductCard product={product as IGood} />
      </MemoryRouter>
    </Provider>
  );
};

const BASE = { id: 1, name: 'Garden shears', picUrl: '/x.jpg', retailPrice: 19.9, counterPrice: 19.9 };

describe('EU stock on a product card', () => {
  it('shows the badge on a measured EU-stocked product', () => {
    card({ ...BASE, eu_flag: 1 });
    expect(screen.getByText('EU stock')).toBeTruthy();
  });

  it('says nothing when the flag is 0 — unprobed and probed-empty are the same fact', () => {
    card({ ...BASE, eu_flag: 0 });
    expect(screen.queryByText('EU stock')).toBeNull();
  });

  it('says nothing when the field is absent, as on the home and list payloads', () => {
    card(BASE);
    expect(screen.queryByText('EU stock')).toBeNull();
  });

  it('tolerates the camelCase spelling and a stringified 1', () => {
    card({ ...BASE, euFlag: '1' });
    expect(screen.getByText('EU stock')).toBeTruthy();
  });

  it('states a stock reading, never a delivery window', () => {
    card({ ...BASE, eu_flag: 1 });
    // "2-4 days" would be a number nobody measured: CJ picks the fulfilling
    // warehouse at order time, and the reading can be stale by then.
    expect(screen.queryByText(/\d+\s*[-–]\s*\d+\s*days?/i)).toBeNull();
    expect(screen.getByTitle(/at our last stock check/i)).toBeTruthy();
  });

  it('never takes the overlay slot from a live deal', () => {
    // Badge discipline: ONE overlay badge, offers only. EU stock is a
    // fulfilment fact and rides the logistics line, so both must show.
    card({ ...BASE, eu_flag: 1, hot: true });
    expect(screen.getByText('Limited time deal')).toBeTruthy();
    expect(screen.getByText('EU stock')).toBeTruthy();
  });

  it('coexists with a coupon chip rather than displacing it', () => {
    card({ ...BASE, eu_flag: 1, coupon_flag: 1 });
    expect(screen.getByText('Coupon')).toBeTruthy();
    expect(screen.getByText('EU stock')).toBeTruthy();
  });
});
