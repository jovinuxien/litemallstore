import React, { useEffect, useState } from 'react';

import { baseAxios, SRV, unwrap } from 'app/shared/api';
import { useTranslation } from 'app/i18n';

/**
 * Live flash-deal banner on the product detail page. Fetches
 * `GET /srv/goods/deal?id=` once (data:null when no deal is live — the price
 * itself already rides the normal detail payload via the price-swap) and
 * renders a countdown (1s tick — this is the hero surface, unlike the cards'
 * 30s tick) plus the claimed bar for capped deals. Renders nothing on error,
 * for CJ goods (no deals by design), or when the countdown reaches zero.
 */

interface DealBlock {
  dealPrice?: number;
  originalPrice?: number;
  endEpoch?: number;
  stock?: number;
  claimed?: number;
  claimedPct?: number | null;
}

const pad = (n: number): string => String(n).padStart(2, '0');

const fmtClock = (ms: number): string => {
  const s = Math.max(0, Math.floor(ms / 1000));
  const d = Math.floor(s / 86400);
  const clock = `${pad(Math.floor((s % 86400) / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`;
  return d > 0 ? `${d}d ${clock}` : clock;
};

const DealBanner: React.FC<{ goodsId?: number; isCj: boolean }> = ({ goodsId, isCj }) => {
  const { t } = useTranslation('product');
  const [deal, setDeal] = useState<DealBlock | null>(null);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (goodsId == null || isCj) return undefined;
    let cancelled = false;
    unwrap<DealBlock | null>(baseAxios.get(`${SRV}/goods/deal`, { params: { id: goodsId } }))
      .then(d => {
        if (!cancelled) setDeal(d && typeof d.endEpoch === 'number' ? d : null);
      })
      .catch(() => {
        if (!cancelled) setDeal(null);
      });
    return () => {
      cancelled = true;
    };
  }, [goodsId, isCj]);

  useEffect(() => {
    if (!deal) return undefined;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [deal]);

  if (!deal || deal.endEpoch == null || deal.endEpoch <= now) return null;
  const claimedPct = typeof deal.claimedPct === 'number' ? Math.min(100, deal.claimedPct) : null;

  return (
    <div
      className='lm-pdp__dealbanner'
      style={{
        background: '#CC0C39',
        color: '#fff',
        borderRadius: 6,
        padding: '8px 12px',
        margin: '8px 0',
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        flexWrap: 'wrap',
      }}
    >
      <strong>{t('deal.title')}</strong>
      <span style={{ fontVariantNumeric: 'tabular-nums' }}>{t('deal.endsIn', { clock: fmtClock(deal.endEpoch - now) })}</span>
      {claimedPct != null && claimedPct > 0 && (
        <span style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 140, flex: 1 }}>
          <span style={{ flex: 1, height: 6, borderRadius: 3, background: 'rgba(255,255,255,0.35)', overflow: 'hidden' }}>
            <span style={{ display: 'block', width: `${claimedPct}%`, height: '100%', background: '#fff' }} />
          </span>
          <span style={{ fontSize: '0.8rem' }}>{t('deal.claimed', { pct: claimedPct })}</span>
        </span>
      )}
    </div>
  );
};

export default DealBanner;
