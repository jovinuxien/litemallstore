import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { contentApi, IBrand } from 'app/shared/api';
import { Attribution, attributionOf } from 'app/shared/util/attribution';

/**
 * Wave-25 honest attribution row under the PDP title. Renders ONLY when the
 * product points at a display-enabled brand row: kind=1 → "Sold by <name>" +
 * "More from this store"; kind=0 → "Brand: <name>". Both link the existing
 * /brand/:id page. brandId 0, a disabled row (raw CJ supplier legal names),
 * or any fetch failure ⇒ nothing — never a fake attribution.
 */

// Session cache: PDPs revisit the same handful of brands.
const cache = new Map<number, Attribution | null>();

/** Test hook — jest clears it between cases. */
export const clearSoldByCache = () => cache.clear();

const SoldByRow: React.FC<{ brandId: number }> = ({ brandId }) => {
  const [attribution, setAttribution] = useState<Attribution | null>(() => (brandId > 0 ? cache.get(brandId) ?? null : null));

  useEffect(() => {
    if (brandId <= 0 || cache.has(brandId)) {
      setAttribution(brandId > 0 ? cache.get(brandId) ?? null : null);
      return;
    }
    let cancelled = false;
    contentApi
      .brandDetail(brandId)
      .then((b: IBrand | null) => {
        const a = attributionOf(b);
        cache.set(brandId, a);
        if (!cancelled) setAttribution(a);
      })
      .catch(() => {
        // Fail-closed and silent: no row beats a wrong row. Not cached, so a
        // transient error doesn't stick for the session.
        if (!cancelled) setAttribution(null);
      });
    return () => {
      cancelled = true;
    };
  }, [brandId]);

  if (!attribution) return null;

  return (
    <div className='lm-pdp__soldby'>
      {attribution.label === 'store' ? (
        <>
          Sold by <Link to={`/brand/${brandId}`}>{attribution.name}</Link>
          <Link to={`/brand/${brandId}`} className='lm-pdp__soldby-more'>
            More from this store
          </Link>
        </>
      ) : (
        <>
          Brand: <Link to={`/brand/${brandId}`}>{attribution.name}</Link>
        </>
      )}
    </div>
  );
};

export default SoldByRow;
