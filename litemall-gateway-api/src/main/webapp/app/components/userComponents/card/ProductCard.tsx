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
  // Optional pre-rendered title (search-highlight snippets as React nodes);
  // falls back to the plain name. The tooltip/alt/cart name stay plain text.
  nameNode?: React.ReactNode;
}

/**
 * Storefront product card, laid out like a cjdropshipping.com catalog card —
 * a clean vertical stack: square image, 2-line title, a small meta line
 * (rating / sold, CJ's "Lists:" analog), then the price — restyled onto the
 * storefront's Teal & Coral tokens (--lm-* in product-card.scss), so the CJ
 * structure wears this shop's colors. The discount badge overlays the image
 * (the stack below stays as clean as CJ's); flash-deal countdown, free
 * shipping and the add-to-cart CTA keep their places below the price.
 * Deliberately NOT copied from CJ: the warehouse/country chips.
 *
 * A quantity stepper rides next to the CTA so a shopper can drop N of a
 * product into the cart straight from the grid (SKU/variant choice still
 * happens on the detail page).
 */
const ProductCard: React.FC<Props> = ({ product, nameNode }) => {
  const dispatch = useAppDispatch();
  const [added, setAdded] = useState(false);
  const [qty, setQty] = useState(1);
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

  // Quick add: drop the goods into the (local) cart at the stepper's quantity.
  // SKU/spec selection still happens on the detail page; this gives the
  // marketplace an N-tap-free add. Carry `source` so a CJ line is recognized at
  // checkout — a CJ quick-add has no chosen variant (productId), so checkout
  // will prompt the shopper to open it and pick one.
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
        number: qty,
        picUrl,
        specifications: [],
        checked: true,
        source: p.source,
      })
    );
    setAdded(true);
    setQty(1);
    setTimeout(() => setAdded(false), 1400);
  };

  const stepQty = (e: React.MouseEvent, delta: number) => {
    e.preventDefault();
    e.stopPropagation();
    setQty(prev => Math.min(999, Math.max(1, prev + delta)));
  };

  return (
    <div className="lm-card">
      {/* Square, CJ-style image; the discount % and deal flag overlay it so the
          text stack below stays as clean as CJ's. */}
      <Link to={to} className="lm-card__media">
        <img className="lm-card__img" src={picUrl} alt={name} loading="lazy" />
        {hasDiscount && <span className="lm-card__discount">-{discountPct}%</span>}
        {isHot && <span className="lm-card__deal-label">Limited time deal</span>}
      </Link>

      <div className="lm-card__body">
        {/* CJ stack: title → meta count line → price. */}
        <Link to={to} className="lm-card__title" title={name}>
          {nameNode ?? name}
        </Link>

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

        <div className="lm-card__price-row">
          <span className="lm-card__price">
            <span className="lm-card__cur">US&nbsp;$</span>
            {fmtPrice(retail)}
          </span>
          {hasDiscount && (
            <span className="lm-card__orig">
              List: <s>US&nbsp;${fmtPrice(counter)}</s>
            </span>
          )}
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

        {p.isFreeShipping && <div className="lm-card__shipping">🚚 Free shipping</div>}

        <div className="lm-card__actions">
          <div className="lm-card__qty" aria-label="Quantity">
            <button type="button" className="lm-card__qty-btn" onClick={e => stepQty(e, -1)} disabled={qty <= 1} aria-label="Decrease quantity">
              −
            </button>
            <span className="lm-card__qty-val">{qty}</span>
            <button type="button" className="lm-card__qty-btn" onClick={e => stepQty(e, 1)} aria-label="Increase quantity">
              +
            </button>
          </div>
          <button type="button" className={`lm-card__cart${added ? ' lm-card__cart--added' : ''}`} onClick={handleAddToCart}>
            {added ? 'Added ✓' : 'Add to cart'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default ProductCard;
