import React from 'react';
import { render, waitFor } from '@testing-library/react';

import RatingSummary, { starIcons } from './RatingSummary';

/**
 * The rating row is strictly decorative: it renders only for a numeric goods
 * id whose meta carries a positive rating AND review count, and any fetch
 * failure renders nothing — the PDP must never depend on the meta endpoint.
 */
const goodsMeta = jest.fn();
jest.mock('app/shared/api', () => ({
  ...jest.requireActual('app/shared/api'),
  catalogApi: { goodsMeta: (...args: unknown[]) => goodsMeta(...args) },
}));

describe('RatingSummary', () => {
  beforeEach(() => goodsMeta.mockReset());

  it('renders stars, the average and the ratings link from meta', async () => {
    goodsMeta.mockResolvedValue({ rating: 4.4, reviewCount: 128 });
    const { container, getByText } = render(<RatingSummary goodsId={10008302} />);
    await waitFor(() => expect(container.querySelector('.lm-pdp__ratingrow')).not.toBeNull());
    expect(getByText('4.4')).not.toBeNull();
    expect(getByText('128 ratings')).not.toBeNull();
  });

  it('renders nothing when the product has no reviews yet', async () => {
    goodsMeta.mockResolvedValue({ rating: null, reviewCount: 0 });
    const { container } = render(<RatingSummary goodsId={1} />);
    await waitFor(() => expect(goodsMeta).toHaveBeenCalled());
    expect(container.querySelector('.lm-pdp__ratingrow')).toBeNull();
  });

  it('renders nothing when the meta fetch fails (fail-silent)', async () => {
    goodsMeta.mockRejectedValue(new Error('down'));
    const { container } = render(<RatingSummary goodsId={1} />);
    await waitFor(() => expect(goodsMeta).toHaveBeenCalled());
    expect(container.querySelector('.lm-pdp__ratingrow')).toBeNull();
  });

  it('skips the fetch entirely for non-numeric (legacy cj_) ids', () => {
    const { container } = render(<RatingSummary goodsId='cj_12345' />);
    expect(goodsMeta).not.toHaveBeenCalled();
    expect(container.querySelector('.lm-pdp__ratingrow')).toBeNull();
  });

  it('maps ratings to full/half/empty star icons (half-rounded)', () => {
    expect(starIcons(4.4)).toEqual(['bi-star-fill', 'bi-star-fill', 'bi-star-fill', 'bi-star-fill', 'bi-star-half']);
    expect(starIcons(5)).toEqual(Array(5).fill('bi-star-fill'));
    expect(starIcons(2.1)).toEqual(['bi-star-fill', 'bi-star-fill', 'bi-star', 'bi-star', 'bi-star']);
  });
});
