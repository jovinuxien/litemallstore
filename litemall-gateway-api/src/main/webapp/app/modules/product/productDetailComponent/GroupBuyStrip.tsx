import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { ICombination, promotionApi } from 'app/shared/api';
import { priceNum } from 'app/components/userComponents/card/ProductCard';

/**
 * Wave-21 PDP group-buy entry. When an active combination campaign exists for
 * this goods (`GET /srv/promotion/combination/active`, filtered client-side by
 * goodsId), a teal strip shows the group price + members needed with a
 * "Start a group" CTA: start reserves the slot server-side, the current
 * variant selection is dropped into the cart, and checkout opens carrying the
 * buyer's own `pinkId` — the ORDER SERVICE prices the line at the group price
 * at submit (nothing is computed client-side here).
 *
 * When the shopper already holds a slot (they arrived with `?pinkId=` from the
 * /groupon/:id landing), the strip flips to "Continue to checkout" instead.
 * Renders nothing when no campaign covers this product or the fetch fails.
 */
const GroupBuyStrip: React.FC<{
  goodsId?: number;
  inStock: boolean;
  /** The slot the shopper already holds (?pinkId= route param), if any. */
  heldPinkId?: string | null;
  /** Adds the currently selected variant/qty to the cart (PDP owns that state). */
  prepareCart: () => void;
}> = ({ goodsId, inStock, heldPinkId, prepareCart }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const isAuthenticated = useAppSelector(state => state.customerAuth.data.isAuthenticated);

  const [campaign, setCampaign] = useState<ICombination | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (goodsId == null) return undefined;
    let cancelled = false;
    promotionApi
      .combinationActive()
      .then(list => {
        if (cancelled) return;
        setCampaign((list ?? []).find(c => Number(c.goodsId) === Number(goodsId)) ?? null);
      })
      .catch(() => !cancelled && setCampaign(null));
    return () => {
      cancelled = true;
    };
  }, [goodsId]);

  if (!campaign) return null;

  const groupPrice = priceNum(campaign.combinationPrice);
  const originalPrice = priceNum(campaign.originalPrice);
  const detailTo = campaign.combinationId != null ? `/groupon/${campaign.combinationId}` : '/groupon';

  const toCheckout = (pinkId: number | string) => {
    prepareCart();
    navigate(`/checkout?pinkId=${pinkId}`);
  };

  const startGroup = async () => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: { pathname: `${location.pathname}${location.search}` } } });
      return;
    }
    if (campaign.combinationId == null || busy) return;
    setBusy(true);
    setError(null);
    try {
      const op = await promotionApi.combinationStart(campaign.combinationId);
      const pinkId = Number(op?.data?.pinkId);
      if (Number.isFinite(pinkId) && pinkId > 0) toCheckout(pinkId);
      else setError('The group could not be started — please try again.');
    } catch (e) {
      // 400 carries {success:false,message} — shown verbatim.
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? 'The group could not be started — please try again.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className='lm-pdp__groupbuy'
      style={{
        background: 'var(--lm-primary, #0d7d80)',
        color: '#fff',
        borderRadius: 6,
        padding: '10px 12px',
        margin: '8px 0',
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        flexWrap: 'wrap',
      }}
    >
      <strong>Group buy ${groupPrice.toFixed(2)}</strong>
      {originalPrice > groupPrice && (
        <span style={{ opacity: 0.85 }}>
          <s>${originalPrice.toFixed(2)}</s>
        </span>
      )}
      {campaign.requiredMembers != null && <span style={{ opacity: 0.9 }}>{campaign.requiredMembers} people per group</span>}
      <span style={{ display: 'flex', gap: 8, alignItems: 'center', marginLeft: 'auto', flexWrap: 'wrap' }}>
        {heldPinkId ? (
          <button
            type='button'
            className='btn btn-sm btn-light fw-semibold'
            disabled={!inStock}
            onClick={() => toCheckout(heldPinkId)}
          >
            Continue to checkout
          </button>
        ) : (
          <button type='button' className='btn btn-sm btn-light fw-semibold' disabled={!inStock || busy} onClick={() => void startGroup()}>
            {busy ? 'Starting…' : 'Start a group'}
          </button>
        )}
        <Link to={detailTo} className='text-white small' style={{ whiteSpace: 'nowrap' }}>
          Group details <i className='bi bi-chevron-right' style={{ fontSize: '0.7em' }} />
        </Link>
      </span>
      {heldPinkId && (
        <span className='small' style={{ flexBasis: '100%', opacity: 0.9 }}>
          You hold a group slot — pick your options, then continue. The group price is applied at payment.
        </span>
      )}
      {error && (
        <span className='small' style={{ flexBasis: '100%', background: 'rgba(255,255,255,0.15)', borderRadius: 4, padding: '4px 8px' }} role='status'>
          {error}
        </span>
      )}
    </div>
  );
};

export default GroupBuyStrip;
