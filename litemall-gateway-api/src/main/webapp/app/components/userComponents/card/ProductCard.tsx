import React, { useState } from 'react';
import { Link } from 'react-router-dom';

import { useAppDispatch } from 'app/config/store';
import { addItem } from 'app/shared/reducers/cartSlice';
import { IGood } from 'app/shared/model/product/product.model';
import './product-card.scss';

/**
 * Goods-management returns prices either as a plain number or as a
 * `LitemallMoney { amount }` value object. Read the numeric value defensively.
 */
export const priceNum = (price: unknown): number => {
  if (price == null) return 0;
  if (typeof price === 'number') return price;
  if (typeof price === 'object' && 'amount' in (price as Record<string, unknown>)) {
    return Number((price as { amount: unknown }).amount) || 0;
  }
  const n = Number(price);
  return Number.isFinite(n) ? n : 0;
};

// Resolve a goods id from either IGood (id) or the backend DTO (goodsId:{id}).
// Exported so callers building React keys / links use the same logic as the card.
export const goodId = (raw: unknown): number | undefined => {
  const r = raw as { id?: number; goodsId?: { id?: number } | number } | null;
  if (r == null) return undefined;
  if (r.id != null) return r.id;
  return typeof r.goodsId === 'object' ? r.goodsId?.id : r.goodsId;
};

const fmtPrice = (n: number): string => n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

// Compact "sold" count: 1203 -> "1.2k+".
const fmtSold = (n: number): string => (n >= 1000 ? `${(n / 1000).toFixed(n >= 10000 ? 0 : 1)}k+` : `${n}`);

// "2d 4h" / "3h 12m" / "8m" remaining until a deal-end epoch (ms); null once past.
const fmtRemaining = (endEpoch: number, now: number): string | null => {
  const ms = endEpoch - now;
  if (ms <= 0) return null;
  const m = Math.floor(ms / 60000);
  if (m >= 2880) return `${Math.floor(m / 1440)}d ${Math.floor((m % 1440) / 60)}h`;
  if (m >= 60) return `${Math.floor(m / 60)}h ${m % 60}m`;
  return `${m}m`;
};

/** Ticks every 30s while a deal countdown is on screen. */
const useNow = (active: boolean): number => {
  const [now, setNow] = useState(() => Date.now());
  React.useEffect(() => {
    if (!active) return undefined;
    const t = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(t);
  }, [active]);
  return now;
};

interface Props {
  product: IGood;
}

/**
 * Storefront product card (Teal & Coral theme). Layout mirrors a marketplace
 * listing card; colors come from the CSS variables in product-card.scss.
 */
const ProductCard: React.FC<Props> = ({ product }) => {
  const dispatch = useAppDispatch();
  const [added, setAdded] = useState(false);
  // The backend goods DTO uses goodsName / goodsId:{id} / new / hot, while IGood
  // types them as name / id / isNew / isHot. Read whichever is present so cards
  // work against both the OCS search shape and the home/list payloads.
  const p = product as IGood & {
    goodsId?: { id?: number } | number;
    goodsName?: string;
    new?: boolean;
    hot?: boolean;
    source?: string;
    dealActive?: boolean;
    dealEndEpoch?: number;
    dealClaimedPct?: number;
  };
  const id = goodId(p);
  const name = p.name ?? p.goodsName ?? '';
  const isNew = p.isNew ?? p.new;
  const isHot = p.isHot ?? p.hot;
  const picUrl = p.picUrl;
  const retail = priceNum(p.retailPrice);
  const counter = priceNum(p.counterPrice);
  const hasDiscount = counter > retail && retail > 0;
  const discountPct = hasDiscount ? Math.round(((counter - retail) / counter) * 100) : 0;
  const sold = Number(p.salesQuantity) || 0;
  const rating = Number(p.star) || 0;
  const to = `/product/${id}`;
  // Live flash deal: countdown chip (30s tick) + claimed bar. Fields ride the search DTO
  // only while a deal is live, so this renders nothing everywhere else.
  const dealEnd = p.dealActive && typeof p.dealEndEpoch === 'number' ? p.dealEndEpoch : undefined;
  const now = useNow(dealEnd != null);
  const remaining = dealEnd != null ? fmtRemaining(dealEnd, now) : null;
  const claimedPct = p.dealActive && typeof p.dealClaimedPct === 'number' ? Math.min(100, p.dealClaimedPct) : undefined;

  // Quick add: drop the goods into the (local) cart at qty 1. SKU/spec selection
  // still happens on the detail page; this gives the marketplace one-tap add.
  // Carry `source` so a CJ line is recognized at checkout — a CJ quick-add has no chosen
  // variant (productId), so checkout will prompt the shopper to open it and pick one.
  const handleAddToCart = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (id == null) return;
    dispatch(
      addItem({
        id,
        goodsId: String(id),
        goodsName: name,
        price: retail,
        number: 1,
        picUrl,
        specifications: [],
        checked: true,
        source: p.source,
      })
    );
    setAdded(true);
    setTimeout(() => setAdded(false), 1400);
  };

  return (
    <div className="lm-card">
      <Link to={to} className="lm-card__media">
        <img className="lm-card__img" src={picUrl} alt={name} loading="lazy" />
        {hasDiscount && <span className="lm-card__discount">-{discountPct}%</span>}
        <div className="lm-card__ribbons">
          {isNew && <span className="lm-card__ribbon lm-card__ribbon--new">New</span>}
          {isHot && <span className="lm-card__ribbon lm-card__ribbon--hot">Hot</span>}
        </div>
      </Link>

      <div className="lm-card__body">
        <Link to={to} className="lm-card__title" title={name}>
          {name}
        </Link>

        <div className="lm-card__price-row">
          <span className="lm-card__price">
            <span className="lm-card__cur">US&nbsp;$</span>
            {fmtPrice(retail)}
          </span>
          {hasDiscount && <span className="lm-card__orig">US&nbsp;${fmtPrice(counter)}</span>}
        </div>

        {remaining && (
          <div className="lm-card__deal" style={{ fontSize: '0.78rem', color: '#CC0C39', fontWeight: 600 }}>
            ⏱ Ends in {remaining}
            {claimedPct != null && claimedPct > 0 && <span style={{ marginLeft: 6, color: '#6c757d', fontWeight: 400 }}>{claimedPct}% claimed</span>}
          </div>
        )}
        {remaining && claimedPct != null && claimedPct > 0 && (
          <div style={{ height: 4, borderRadius: 2, background: '#f1f3f5', overflow: 'hidden', margin: '2px 0 4px' }}>
            <div style={{ width: `${claimedPct}%`, height: '100%', background: '#CC0C39' }} />
          </div>
        )}

        <div className="lm-card__meta">
          {rating > 0 && (
            <span className="lm-card__rating">
              <span className="lm-card__star">★</span>
              {rating.toFixed(1)}
              {Number(p.reviewCount) > 0 && <span className="lm-card__rcount">({Number(p.reviewCount)})</span>}
            </span>
          )}
          {sold > 0 && <span className="lm-card__sold">{fmtSold(sold)} sold</span>}
        </div>

        {p.isFreeShipping && <div className="lm-card__shipping">🚚 Free shipping</div>}

        <button type="button" className={`lm-card__cart${added ? ' lm-card__cart--added' : ''}`} onClick={handleAddToCart}>
          {added ? 'Added ✓' : 'Add to cart'}
        </button>
      </div>
    </div>
  );
};

export default ProductCard;
