import React, { useEffect, useState } from 'react';

import { catalogApi } from 'app/shared/api';

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

/** Full/half/empty star icon classes for a 0–5 rating, half-rounded. */
export const starIcons = (rating: number): string[] => {
  const r = Math.round(Math.max(0, Math.min(5, rating)) * 2) / 2;
  return [1, 2, 3, 4, 5].map(i => (i <= r ? 'bi-star-fill' : i - 0.5 === r ? 'bi-star-half' : 'bi-star'));
};

const RatingSummary: React.FC<Props> = ({ goodsId }) => {
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
      <span className='lm-pdp__stars' aria-label={`${rating.toFixed(1)} of 5 stars`}>
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
        {count.toLocaleString()} rating{count === 1 ? '' : 's'}
      </button>
    </div>
  );
};

export default RatingSummary;
