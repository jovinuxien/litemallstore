import React from 'react';
import { render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import Breadcrumb from './Breadcrumb';

/**
 * PDP breadcrumb: the ancestor chain comes from
 * `GET /srv/search/category/{leaf}?size=1` (`data.breadcrumb`); the component
 * is strictly decorative — no categoryIds, a failed fetch, or an empty chain
 * all render nothing.
 */
const get = jest.fn();
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: (...args: unknown[]) => get(...args) },
}));

const chain = [
  { id: 1036495, name: 'Home Improvement', level: 'L1' },
  { id: 1036504, name: 'Tools', level: 'L2' },
  { id: 1036513, name: 'Power Tools', level: 'L3' },
];

const renderCrumbs = (categoryIds?: Array<number | string>) =>
  render(
    <MemoryRouter>
      <Breadcrumb categoryIds={categoryIds} />
    </MemoryRouter>
  );

describe('PDP Breadcrumb', () => {
  beforeEach(() => get.mockReset());

  it('renders Home › the fetched ancestor chain, each crumb linking its landing', async () => {
    get.mockResolvedValue({ data: { errno: 0, data: { breadcrumb: chain } } });
    const { container, getByText } = renderCrumbs([1036513, 1036513]);
    await waitFor(() => expect(container.querySelector('.lm-pdp__crumbs')).not.toBeNull());
    expect(get).toHaveBeenCalledWith('/srv/search/category/1036513?size=1');
    expect(getByText('Home').getAttribute('href')).toBe('/');
    expect(getByText('Tools').getAttribute('href')).toBe('/category/1036504');
    expect(getByText('Power Tools').getAttribute('href')).toBe('/category/1036513');
  });

  it('renders nothing when the fetch fails (fail-silent)', async () => {
    get.mockRejectedValue(new Error('down'));
    const { container } = renderCrumbs([1036513]);
    await waitFor(() => expect(get).toHaveBeenCalled());
    expect(container.querySelector('.lm-pdp__crumbs')).toBeNull();
  });

  it('renders nothing on an empty chain', async () => {
    get.mockResolvedValue({ data: { errno: 0, data: { breadcrumb: [] } } });
    const { container } = renderCrumbs([1036513]);
    await waitFor(() => expect(get).toHaveBeenCalled());
    expect(container.querySelector('.lm-pdp__crumbs')).toBeNull();
  });

  it('skips the fetch entirely without a numeric category id', () => {
    const { container } = renderCrumbs(['cj_x' as any]);
    renderCrumbs(undefined);
    expect(get).not.toHaveBeenCalled();
    expect(container.firstChild).toBeNull();
  });
});
