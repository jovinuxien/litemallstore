import React from 'react';

import { originCountryName } from 'app/shared/util/euStock';

/**
 * Wave-28 warehouse-origin line for a cart/checkout row.
 *
 * Renders ONLY when the code names an EU warehouse we actually stock from. An
 * unmeasured product, or one quoted from outside the EU, renders nothing — the
 * same rule the PDP badge follows, and for the same reason: this signal exists to
 * tell a customer something true and good about delivery, not to caption every
 * line with a shrug.
 *
 * Deliberately no delivery estimate. Stock being in an EU warehouse is what was
 * measured; CJ still picks the fulfilling warehouse at order time.
 */
const OriginNote: React.FC<{ countryCode?: string | null }> = ({ countryCode }) => {
  const name = originCountryName(countryCode);
  if (!name) return null;

  return (
    <span className='lm-origin-note'>
      <i className='bi bi-geo-alt me-1' aria-hidden='true' />
      Ships from {name}
    </span>
  );
};

export default OriginNote;
