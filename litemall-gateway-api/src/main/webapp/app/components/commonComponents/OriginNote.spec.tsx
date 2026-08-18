import React from 'react';
import { render, screen } from '@testing-library/react';

import OriginNote from './OriginNote';

describe('OriginNote', () => {
  it('names a measured EU warehouse', () => {
    render(<OriginNote countryCode='DE' />);
    expect(screen.getByText(/Ships from Germany/)).toBeTruthy();
  });

  it('renders nothing when the goods was never measured', () => {
    // The batch origin endpoint omits unmeasured goods entirely, so the common
    // case at checkout is an undefined code — and the common case must be silent.
    const { container } = render(<OriginNote countryCode={undefined} />);
    expect(container.innerHTML).toBe('');
  });

  it('renders nothing for a non-EU origin rather than captioning it', () => {
    const { container } = render(<OriginNote countryCode='CN' />);
    expect(container.innerHTML).toBe('');
  });

  it('promises no delivery window', () => {
    // Stock existing in an EU warehouse is what was measured. CJ still picks the
    // fulfilling warehouse at order time, so any day count here would be invented.
    render(<OriginNote countryCode='DE' />);
    expect(screen.queryByText(/\d+\s*[-–]\s*\d+\s*days?/i)).toBeNull();
    expect(screen.queryByText(/deliver(y|ed) (in|by|within)/i)).toBeNull();
  });
});
