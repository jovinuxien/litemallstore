import React from 'react';
import { render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Wave-20 palette v1.1 renderer branches: the new `groupon-strip`
 * (auto/explicit/cap/R1-empty) and the extended `coupon-strip`
 * (v1 backward-compat, explicit couponIds, headline, grid style).
 *
 * The network seam is the shared axios instance — mocked whole so no
 * interceptor code runs. Both promotion endpoints return BARE arrays
 * (no {errno} envelope); `unwrap` passes them through verbatim.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

import { baseAxios } from 'app/config/axiosinstance';
import PageRenderer from './PageRenderer';
import { IPageView } from 'app/shared/api';

const mockGet = baseAxios.get as jest.Mock;

const combinations = [
  { combinationId: 1, goodsId: 501, title: 'Ceramic table lamp', picUrl: '/_cdn/cf/1.jpg', combinationPrice: 12.5, originalPrice: 19.99, requiredMembers: 2 },
  { combinationId: 2, goodsId: 502, title: 'Steel bottle', picUrl: '/_cdn/cf/2.jpg', combinationPrice: 8, originalPrice: 11, requiredMembers: 3 },
  { combinationId: 3, goodsId: 503, title: 'Desk lamp', picUrl: '/_cdn/cf/3.jpg', combinationPrice: 20, originalPrice: 30, requiredMembers: 5 },
];

const coupons = [
  { couponId: 11, name: 'Welcome', discount: 5, min: 30 },
  { couponId: 12, name: 'Summer', discount: 10, min: 60 },
  { couponId: 13, name: 'Percent', discount: 15, min: 0, discountType: 1, discountCap: 20 },
];

const wireNetwork = (opts: { combinationsFail?: boolean; activeList?: unknown } = {}) => {
  mockGet.mockImplementation((url: string) => {
    if (url.includes('/promotion/combination/active')) {
      return opts.combinationsFail
        ? Promise.reject(new Error('down'))
        : Promise.resolve({ data: opts.activeList ?? combinations });
    }
    if (url.includes('/promotion/coupon/available')) {
      return Promise.resolve({ data: coupons });
    }
    return Promise.reject(new Error(`unexpected GET ${url}`));
  });
};

const page = (type: string, config: Record<string, unknown>): IPageView => ({
  id: 1,
  name: 'Test page',
  components: [{ type, config }],
});

const renderPage = (p: IPageView) =>
  render(
    <MemoryRouter>
      <PageRenderer page={p} />
    </MemoryRouter>
  );

beforeEach(() => {
  mockGet.mockReset();
});

describe('groupon-strip (palette v1.1)', () => {
  it('auto mode renders every active campaign up to the default max of 4', async () => {
    wireNetwork();
    const { container } = renderPage(page('groupon-strip', {}));
    await waitFor(() => expect(container.querySelectorAll('.lm-rail > *')).toHaveLength(3));

    // Default section title and the teaser copy — NO price-promise CTA
    // ("buy at group price" is Phase-3-gated); the card links the PRODUCT page.
    expect(container.querySelector('.lm-section__title')!.textContent).toBe('Group up & save');
    const first = container.querySelector('.lm-rail > a') as HTMLAnchorElement;
    expect(first.getAttribute('href')).toBe('/product/501-ceramic-table-lamp');
    expect(first.textContent).toContain('Ceramic table lamp');
    expect(first.textContent).toContain('$12.5');
    expect(first.textContent).toContain('$19.99');
    expect(first.textContent).toContain('2-person group');
    expect(first.textContent).toContain('Group up & save');
    expect(container.textContent!.toLowerCase()).not.toContain('buy at');
  });

  it('explicit combinationIds filter to those campaigns in configured order', async () => {
    wireNetwork();
    const { container } = renderPage(page('groupon-strip', { combinationIds: [3, 1] }));
    await waitFor(() => expect(container.querySelectorAll('.lm-rail > *')).toHaveLength(2));

    const names = Array.from(container.querySelectorAll('.lm-rail .text-truncate')).map(n => n.textContent);
    expect(names).toEqual(['Desk lamp', 'Ceramic table lamp']);
  });

  it('caps at maxItems', async () => {
    wireNetwork();
    const { container } = renderPage(page('groupon-strip', { maxItems: 2 }));
    await waitFor(() => expect(container.querySelectorAll('.lm-rail > *')).toHaveLength(2));
  });

  it('R1: fetch failure renders nothing', async () => {
    wireNetwork({ combinationsFail: true });
    const { container } = renderPage(page('groupon-strip', {}));
    await waitFor(() => expect(mockGet).toHaveBeenCalled());
    await waitFor(() => expect(container.querySelector('.lm-section')).toBeNull());
  });

  it('R1: zero active campaigns renders nothing', async () => {
    wireNetwork({ activeList: [] });
    const { container } = renderPage(page('groupon-strip', { title: 'Group deals' }));
    await waitFor(() => expect(mockGet).toHaveBeenCalled());
    await waitFor(() => expect(container.querySelector('.lm-section')).toBeNull());
  });
});

describe('coupon-strip (v1 backward-compat + v1.1 extensions)', () => {
  it('v1 config renders exactly as before: strip class, no headline, $ amounts, limit slice', async () => {
    wireNetwork();
    const { container } = renderPage(page('coupon-strip', { title: 'Coupons', limit: 2 }));
    await waitFor(() => expect(container.querySelectorAll('.lm-coupon')).toHaveLength(2));

    const wrapper = container.querySelector('.lm-coupons') as HTMLElement;
    expect(wrapper.className).toBe('lm-coupons'); // no --grid modifier
    expect(container.querySelector('.lm-coupons__headline')).toBeNull();
    const amounts = Array.from(container.querySelectorAll('.lm-coupon__amount')).map(n => n.textContent);
    expect(amounts).toEqual(['$5', '$10']);
    expect((container.querySelector('.lm-coupon') as HTMLAnchorElement).getAttribute('href')).toBe('/user/coupons');
  });

  it('explicit couponIds pick those coupons (configured order, no default slice)', async () => {
    wireNetwork();
    const { container } = renderPage(page('coupon-strip', { couponIds: [13, 11] }));
    await waitFor(() => expect(container.querySelectorAll('.lm-coupon')).toHaveLength(2));

    const names = Array.from(container.querySelectorAll('.lm-coupon__name')).map(n => n.textContent);
    expect(names).toEqual(['Percent', 'Welcome']);
    // Wave-18 percent coupon shows its rate, flat stays dollars.
    const amounts = Array.from(container.querySelectorAll('.lm-coupon__amount')).map(n => n.textContent);
    expect(amounts).toEqual(['15%', '$5']);
  });

  it('an explicitly picked coupon missing from the available list is skipped (R1)', async () => {
    wireNetwork();
    const { container } = renderPage(page('coupon-strip', { couponIds: [999, 12] }));
    await waitFor(() => expect(container.querySelectorAll('.lm-coupon')).toHaveLength(1));
    expect(container.querySelector('.lm-coupon__name')!.textContent).toBe('Summer');
  });

  it('grid style + headline render the v1.1 chrome', async () => {
    wireNetwork();
    const { container } = renderPage(page('coupon-strip', { headline: 'Stack your savings', style: 'grid' }));
    await waitFor(() => expect(container.querySelectorAll('.lm-coupon')).toHaveLength(3));

    expect((container.querySelector('.lm-coupons') as HTMLElement).className).toBe('lm-coupons lm-coupons--grid');
    expect(container.querySelector('.lm-coupons__headline')!.textContent).toBe('Stack your savings');
  });
});
