import React from 'react';
import { render } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';

import store from 'app/config/store';

import ProductCard from './ProductCard';

/**
 * Wave-19 coupon badge: a teal "Coupon" pill renders on the card when the
 * search hit carries `coupon_flag: 1` (numeric OCS source field; missing or 0
 * means no badge), sitting in the same overlay row as the deal strip.
 */

const renderCard = (product: any) =>
  render(
    <Provider store={store}>
      <MemoryRouter>
        <ProductCard product={product} />
      </MemoryRouter>
    </Provider>
  );

const base = { id: 10008302, name: 'Ceramic table lamp', picUrl: '/img.jpg', retailPrice: 19.99 };

describe('ProductCard coupon badge', () => {
  it('shows the Coupon pill when the hit has coupon_flag: 1', () => {
    const { container } = renderCard({ ...base, coupon_flag: 1 });
    const pill = container.querySelector('.lm-card__coupon');
    expect(pill).not.toBeNull();
    expect(pill!.textContent).toBe('Coupon');
  });

  it('shows no pill when coupon_flag is 0', () => {
    const { container } = renderCard({ ...base, coupon_flag: 0 });
    expect(container.querySelector('.lm-card__coupon')).toBeNull();
  });

  it('shows no pill when the field is absent (old index docs / list DTOs)', () => {
    const { container } = renderCard(base);
    expect(container.querySelector('.lm-card__coupon')).toBeNull();
    expect(container.querySelector('.lm-card__flags')).toBeNull();
  });

  it('tolerates the camelCase couponFlag spelling and string "1"', () => {
    expect(renderCard({ ...base, couponFlag: 1 }).container.querySelector('.lm-card__coupon')).not.toBeNull();
    expect(renderCard({ ...base, coupon_flag: '1' }).container.querySelector('.lm-card__coupon')).not.toBeNull();
  });

  it('renders next to the deal strip when both signals are present', () => {
    const { container } = renderCard({ ...base, hot: true, coupon_flag: 1 });
    const flags = container.querySelector('.lm-card__flags');
    expect(flags).not.toBeNull();
    const children = Array.from(flags!.children).map(c => c.className);
    expect(children).toEqual(['lm-card__deal-label', 'lm-card__coupon']);
  });
});

/**
 * Wave-21 group-buy badge: a coral "Group buy" pill renders when the search
 * hit carries `groupon_flag: 1` — the exact coupon_flag contract for the new
 * OCS source field (missing or 0 means no badge).
 */
describe('ProductCard group-buy badge', () => {
  it('shows the Group buy pill when the hit has groupon_flag: 1', () => {
    const { container } = renderCard({ ...base, groupon_flag: 1 });
    const pill = container.querySelector('.lm-card__groupon');
    expect(pill).not.toBeNull();
    expect(pill!.textContent).toBe('Group buy');
  });

  it('shows no pill when groupon_flag is 0', () => {
    const { container } = renderCard({ ...base, groupon_flag: 0 });
    expect(container.querySelector('.lm-card__groupon')).toBeNull();
  });

  it('shows no pill when the field is absent (old index docs / list DTOs)', () => {
    const { container } = renderCard(base);
    expect(container.querySelector('.lm-card__groupon')).toBeNull();
  });

  it('tolerates the camelCase grouponFlag spelling and string "1"', () => {
    expect(renderCard({ ...base, grouponFlag: 1 }).container.querySelector('.lm-card__groupon')).not.toBeNull();
    expect(renderCard({ ...base, groupon_flag: '1' }).container.querySelector('.lm-card__groupon')).not.toBeNull();
  });

  it('stacks after the deal strip and the coupon pill when all three are present', () => {
    const { container } = renderCard({ ...base, hot: true, coupon_flag: 1, groupon_flag: 1 });
    const flags = container.querySelector('.lm-card__flags');
    expect(flags).not.toBeNull();
    const children = Array.from(flags!.children).map(c => c.className);
    expect(children).toEqual(['lm-card__deal-label', 'lm-card__coupon', 'lm-card__groupon']);
  });
});
