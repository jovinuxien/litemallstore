import React from 'react';
import { render, screen } from '@testing-library/react';

import EuStockBadge from './EuStockBadge';
import { euStockOf, euStockLabel } from 'app/shared/util/euStock';

/**
 * Wave 26: the badge claims a delivery advantage, so the tests are mostly about when it must
 * STAY SILENT. EU capture coverage grows slowly with the CJ enrichment rotation, so "no reading
 * yet" is the common case for a long while and must never render as anything.
 */
describe('EuStockBadge', () => {
  it('renders for a measured, non-zero EU reading', () => {
    render(<EuStockBadge stock={euStockOf({ euStock: { units: 12, countries: ['DE'] } })} />);
    expect(screen.getByText('Ships from Germany')).toBeTruthy();
  });

  it('renders nothing when the product was never probed', () => {
    const { container } = render(<EuStockBadge stock={euStockOf({})} />);
    expect(container.textContent).toBe('');
  });

  it('renders nothing when probed with zero EU stock', () => {
    const { container } = render(<EuStockBadge stock={euStockOf({ euStock: { units: 0, countries: [] } })} />);
    expect(container.textContent).toBe('');
  });

  it('renders nothing for a null/garbage payload', () => {
    expect(euStockOf(null)).toBeNull();
    expect(euStockOf(undefined)).toBeNull();
    expect(euStockOf({ euStock: null })).toBeNull();
    expect(euStockOf({ euStock: { units: undefined as any, countries: undefined as any } })).toBeNull();
  });

  it('falls back to a neutral label when the country is unknown to us', () => {
    // A warehouse country we have no display name for must not print a raw code at the customer.
    expect(euStockLabel({ units: 3, countries: ['XX'] })).toBe('Ships from EU stock');
    expect(euStockLabel({ units: 3, countries: [] })).toBe('Ships from EU stock');
  });

  it('promises no delivery window', () => {
    // What was measured is that stock EXISTS in an EU warehouse — CJ still picks the fulfilling
    // warehouse at order time. A day count here would be a number nobody measured.
    const label = euStockLabel({ units: 5, countries: ['DE'] });
    expect(label).not.toMatch(/\d\s*(-|–|to)?\s*\d*\s*(day|days|hour|hours|week)/i);
  });
});
