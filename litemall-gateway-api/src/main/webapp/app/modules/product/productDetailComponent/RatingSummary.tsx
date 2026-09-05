import React, { useEffect, useState } from 'react';

import { catalogApi } from 'app/shared/api';
import { starIcons } from 'app/shared/util/stars';
import { useTranslation } from 'app/i18n';

/**
 * Amazon-style rating row directly under the title: stars, the average, and a
 * "N ratings" link that scrolls to the reviews section. Data = the Wave-13
 * public meta endpoint (`/srv/goods/meta/{id}` — `rating` is null until first
 * review, `reviewCount` defaults 0). The endpoint takes NUMERIC ids only;
 * legacy `cj_<pid>` routes skip the fetch. Strictly decorative: any failure or
 * empty rating renders nothing — never a blocker for the PDP.
 */
interface Props {
  goodsId?: number | string;
}

export const REVIEWS_ANCHOR_ID = 'lm-reviews';

export { starIcons };

const RatingSummary: React.FC<Props> = ({ goodsId }) => {
  const { t } = useTranslation('product');
  const [meta, setMeta] = useState<{ rating?: number | null; reviewCount?: number } | null>(null);

  useEffect(() => {
    setMeta(null);
    if (goodsId == null || !/^\d+$/.test(String(goodsId))) return;
    let cancelled = false;
    catalogApi
      .goodsMeta(goodsId)
      .then(res => {
        if (!cancelled) setMeta(res ?? null);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [goodsId]);

  const rating = Number(meta?.rating);
  const count = Number(meta?.reviewCount) || 0;
  if (!meta || !Number.isFinite(rating) || rating <= 0 || count <= 0) return null;

  return (
    <div className='lm-pdp__ratingrow'>
      <span className='lm-pdp__stars' aria-label={t('rating.aria', { rating: rating.toFixed(1) })}>
        {starIcons(rating).map((cls, i) => (
          <i key={i} className={`bi ${cls}`} />
        ))}
      </span>
      <span className='lm-pdp__ratingval'>{rating.toFixed(1)}</span>
      <button
        type='button'
        className='lm-pdp__ratingcount'
        onClick={() => document.getElementById(REVIEWS_ANCHOR_ID)?.scrollIntoView({ behavior: 'smooth' })}
      >
        {t('rating.count', { count })}
      </button>
    </div>
  );
};

export default RatingSummary;
