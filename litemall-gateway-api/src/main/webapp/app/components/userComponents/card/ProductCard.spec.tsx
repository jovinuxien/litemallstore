import React from 'react';
import { render } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';

import store from 'app/config/store';

import ProductCard from './ProductCard';

/**
 * Card contracts:
 * - Wave-19/21 signal visibility: coupon_flag/groupon_flag = 1 ⇒ the signal is
 *   ALWAYS visible somewhere on the card (missing or 0 ⇒ nothing).
 * - Amazon-parity badge discipline: at most ONE overlay badge on the image
 *   (deal > discount-% > coupon > group buy); losing signals demote to text
 *   chips under the price (.lm-card__chips).
 * - Five-star icon row, superscript-cents price, single Add-to-cart (no
 *   quantity stepper), stretched title link for whole-card click.
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
});

describe('ProductCard group-buy badge', () => {
  it('shows the Group buy pill when the hit has groupon_flag: 1', () => {
    const { container } = renderCard({ ...base, groupon_flag: 1 });
    const pill = container.querySelector('.lm-card__groupon');
    expect(pill).not.toBeNull();
    expect(pill!.textContent).toBe('Group buy');
  });

  it('shows no pill when groupon_flag is 0 or absent', () => {
    expect(renderCard({ ...base, groupon_flag: 0 }).container.querySelector('.lm-card__groupon')).toBeNull();
    expect(renderCard(base).container.querySelector('.lm-card__groupon')).toBeNull();
  });

  it('tolerates the camelCase grouponFlag spelling and string "1"', () => {
    expect(renderCard({ ...base, grouponFlag: 1 }).container.querySelector('.lm-card__groupon')).not.toBeNull();
    expect(renderCard({ ...base, groupon_flag: '1' }).container.querySelector('.lm-card__groupon')).not.toBeNull();
  });
});

describe('ProductCard badge discipline (one overlay max)', () => {
  it('deal wins the overlay; coupon and group buy demote to chips', () => {
    const { container } = renderCard({ ...base, hot: true, coupon_flag: 1, groupon_flag: 1 });
    const flags = container.querySelector('.lm-card__flags');
    expect(Array.from(flags!.children).map(c => c.className)).toEqual(['lm-card__deal-label']);
    const chips = container.querySelector('.lm-card__chips');
    expect(Array.from(chips!.children).map(c => c.className)).toEqual(['lm-card__coupon', 'lm-card__groupon']);
  });

  it('discount wins over coupon: -% overlay on the image, coupon as a chip', () => {
    const { container } = renderCard({ ...base, counterPrice: 39.98, coupon_flag: 1 });
    expect(container.querySelector('.lm-card__discount')!.textContent).toBe('-50%');
    expect(container.querySelector('.lm-card__flags')).toBeNull();
    expect(container.querySelector('.lm-card__chips .lm-card__coupon')).not.toBeNull();
  });

  it('when deal wins over discount, the -% token moves into the price row', () => {
    const { container } = renderCard({ ...base, hot: true, counterPrice: 39.98 });
    expect(container.querySelector('.lm-card__discount')).toBeNull();
    expect(container.querySelector('.lm-card__price-row .lm-card__disc')!.textContent).toBe('-50%');
  });

  it('coupon alone takes the overlay slot (no chips row)', () => {
    const { container } = renderCard({ ...base, coupon_flag: 1 });
    expect(container.querySelector('.lm-card__flags .lm-card__coupon')).not.toBeNull();
    expect(container.querySelector('.lm-card__chips')).toBeNull();
  });
});

describe('ProductCard Amazon-parity polish', () => {
  it('renders a five-star icon row with the review count', () => {
    const { container } = renderCard({ ...base, star: 4.4, reviewCount: 15 });
    const stars = container.querySelectorAll('.lm-card__stars i');
    expect(stars.length).toBe(5);
    expect(Array.from(stars).filter(i => i.className.includes('bi-star-fill')).length).toBe(4);
    expect(Array.from(stars).filter(i => i.className.includes('bi-star-half')).length).toBe(1);
    expect(container.querySelector('.lm-card__rcount')!.textContent).toBe('(15)');
  });

  it('renders no rating row without a star value', () => {
    expect(renderCard(base).container.querySelector('.lm-card__stars')).toBeNull();
  });

  it('splits the price into big integer + superscript cents', () => {
    const { container } = renderCard(base);
    expect(container.querySelector('.lm-card__cents')!.textContent).toBe('99');
    expect(container.querySelector('.lm-card__price')!.textContent).toContain('19');
  });

  it('has a single Add-to-cart and no quantity stepper', () => {
    const { container } = renderCard(base);
    expect(container.querySelectorAll('.lm-card__cart').length).toBe(1);
    expect(container.querySelector('.lm-card__qty')).toBeNull();
  });

  it('title link is the stretched whole-card target with the slugged href', () => {
    const { container } = renderCard(base);
    const title = container.querySelector('a.lm-card__title');
    expect(title!.getAttribute('href')).toBe('/product/10008302-ceramic-table-lamp');
  });
});
