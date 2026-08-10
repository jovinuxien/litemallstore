import React from 'react';
import { render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import SoldByRow, { clearSoldByCache } from './SoldByRow';

/**
 * The attribution row is honesty-gated: it renders only for a display-enabled
 * brand row, and any fetch failure or disabled row (raw CJ supplier legal
 * names pre-curation) renders nothing.
 */
const brandDetail = jest.fn();
jest.mock('app/shared/api', () => ({
  ...jest.requireActual('app/shared/api'),
  contentApi: { brandDetail: (...args: unknown[]) => brandDetail(...args) },
}));

const renderRow = (brandId: number) =>
  render(
    <MemoryRouter>
      <SoldByRow brandId={brandId} />
    </MemoryRouter>
  );

describe('SoldByRow', () => {
  beforeEach(() => {
    brandDetail.mockReset();
    clearSoldByCache();
  });

  it('renders "Sold by" + the store link for an enabled supplier row (kind=1)', async () => {
    brandDetail.mockResolvedValue({ id: 51, name: 'Sunrise Living', kind: 1, displayEnabled: 1 });
    const { container, getByText } = renderRow(51);
    await waitFor(() => expect(container.querySelector('.lm-pdp__soldby')).not.toBeNull());
    expect(container.textContent).toContain('Sold by');
    expect(getByText('Sunrise Living').getAttribute('href')).toBe('/brand/51');
    expect(getByText('More from this store').getAttribute('href')).toBe('/brand/51');
  });

  it('renders "Brand:" for an enabled consumer brand (kind=0), never the store copy', async () => {
    brandDetail.mockResolvedValue({ id: 3, name: 'Acme', kind: 0, displayEnabled: true });
    const { container, getByText } = renderRow(3);
    await waitFor(() => expect(container.querySelector('.lm-pdp__soldby')).not.toBeNull());
    expect(container.textContent).toContain('Brand:');
    expect(container.textContent).not.toContain('Sold by');
    expect(getByText('Acme').getAttribute('href')).toBe('/brand/3');
  });

  it('renders nothing for a disabled supplier row (curation gate)', async () => {
    brandDetail.mockResolvedValue({ id: 9, name: 'XIN BO EDUCATIONAL CONSULTATION PTE. LTD.', kind: 1, displayEnabled: 0 });
    const { container } = renderRow(9);
    await waitFor(() => expect(brandDetail).toHaveBeenCalled());
    expect(container.textContent).toBe('');
  });

  it('skips the fetch entirely when the goods carries no brand link', () => {
    const { container } = renderRow(0);
    expect(brandDetail).not.toHaveBeenCalled();
    expect(container.textContent).toBe('');
  });

  it('renders nothing when the brand fetch fails (fail-closed, silent)', async () => {
    brandDetail.mockRejectedValue(new Error('down'));
    const { container } = renderRow(4);
    await waitFor(() => expect(brandDetail).toHaveBeenCalled());
    expect(container.textContent).toBe('');
  });
});
