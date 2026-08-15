import React from 'react';

import { EuStock, euStockLabel } from 'app/shared/util/euStock';

/**
 * Wave-26 EU stock badge. Renders ONLY for products with a measured, non-zero EU
 * warehouse reading — the caller passes null for everything else, so an unprobed
 * or China-stocked product shows nothing rather than an implied slow-delivery
 * label.
 *
 * States no delivery window on purpose: what was measured is that stock EXISTS in
 * an EU warehouse. CJ still chooses the fulfilling warehouse at order time, so a
 * day count would be a promise nobody measured.
 */
const EuStockBadge: React.FC<{ stock: EuStock | null }> = ({ stock }) => {
  if (!stock) return null;

  return (
    <span className='lm-pdp__eustock' title='This item is held in an EU warehouse, so it does not ship from China.'>
      <span aria-hidden='true' className='lm-pdp__eustock-dot' />
      {euStockLabel(stock)}
    </span>
  );
};

export default EuStockBadge;
