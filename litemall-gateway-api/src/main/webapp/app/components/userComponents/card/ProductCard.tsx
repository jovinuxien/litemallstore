import React, { useState } from 'react';
import { Link } from 'react-router-dom';

import { useAppDispatch } from 'app/config/store';
import { addItem } from 'app/shared/reducers/cartSlice';
import { IGood } from 'app/shared/model/product/product.model';
import { productPath } from 'app/shared/util/slug';
import { starIcons } from 'app/shared/util/stars';
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
 * Storefront product card — Amazon-parity polish over the CJ-style vertical
 * stack: square image, 2-line title, five-star rating row, price with
 * superscript cents + "List:" strikethrough, then the add-to-cart CTA.
 * BADGE DISCIPLINE: at most ONE overlay badge on the image (deal >
 * discount-% > coupon > group buy); signals that lose the slot demote to
 * small text chips under the price so no card ever stacks a badge wall.
 * The whole card is a click target (stretched title link); the CTA layers
 * above it. Quantity moved to the PDP/cart — grid adds are always qty 1.
 */
const ProductCard: React.FC<Props> = ({ product, nameNode }) => {
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
    coupon_flag?: number | string;
    couponFlag?: number | string;
    groupon_flag?: number | string;
    grouponFlag?: number | string;
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
  // Slugged canonical-shaped href (Wave-13); the route parser accepts both
  // this and the bare-id form.
  const to = id != null ? productPath(id, name) : '/product/undefined';
  // Live flash deal: countdown chip (30s tick) + claimed bar. Fields ride the search DTO
  // only while a deal is live, so this renders nothing everywhere else.
  // Wave-19 coupon visibility: search hits carry the numeric OCS source field
  // `coupon_flag` (1 = an active publicly-claimable coupon's scope covers this
  // product). Always-present 0/1 after reindex; missing (old index docs, or the
  // home/list DTOs that never carry it) means 0. Tolerate a camelCase spelling
  // and string "1" defensively.
  const hasCoupon = Number(p.coupon_flag ?? p.couponFlag ?? 0) === 1;
  // Wave-21 group-buy visibility: same contract as coupon_flag — numeric OCS
  // source field `groupon_flag`, 1 = an ACTIVE combination campaign covers
  // this product; missing (old docs / non-search DTOs) means 0.
  const hasGroupon = Number(p.groupon_flag ?? p.grouponFlag ?? 0) === 1;
  const dealEnd = p.dealActive && typeof p.dealEndEpoch === 'number' ? p.dealEndEpoch : undefined;
  const now = useNow(dealEnd != null);
  const remaining = dealEnd != null ? fmtRemaining(dealEnd, now) : null;
  const claimedPct = p.dealActive && typeof p.dealClaimedPct === 'number' ? Math.min(100, p.dealClaimedPct) : undefined;

  // One overlay badge, by priority; every other signal demotes to a text chip
  // under the price. Discount always keeps its coral "-N%" token in the price
  // row (the PDP pattern), so it never needs a chip.
  const overlay: 'deal' | 'discount' | 'coupon' | 'groupon' | null = isHot
    ? 'deal'
    : hasDiscount
      ? 'discount'
      : hasCoupon
        ? 'coupon'
        : hasGroupon
          ? 'groupon'
          : null;

  // Amazon-style price: integer part big, cents superscript. toFixed keeps the
  // split locale-stable; the List: strikethrough stays locale-formatted.
  const [priceInt, priceCents] = retail.toFixed(2).split('.');

  // Quick add: drop ONE unit into the (local) cart. SKU/spec selection still
  // happens on the detail page. Carry `source` so a CJ line is recognized at
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
      {/* Square image; ONE overlay badge max (priority above). */}
      <Link to={to} className="lm-card__media" tabIndex={-1} aria-hidden="true">
        <img className="lm-card__img" src={picUrl} alt={name} loading="lazy" />
        {overlay === 'discount' && <span className="lm-card__discount">-{discountPct}%</span>}
        {overlay && overlay !== 'discount' && (
          <span className="lm-card__flags">
            {overlay === 'deal' && <span className="lm-card__deal-label">Limited time deal</span>}
            {overlay === 'coupon' && <span className="lm-card__coupon">Coupon</span>}
            {overlay === 'groupon' && <span className="lm-card__groupon">Group buy</span>}
          </span>
        )}
      </Link>

      <div className="lm-card__body">
        {/* Stretched link: this anchor's ::after covers the whole card. */}
        <Link to={to} className="lm-card__title" title={name}>
          {nameNode ?? name}
        </Link>

        <div className="lm-card__meta">
          {rating > 0 && (
            <span className="lm-card__rating" aria-label={`${rating.toFixed(1)} of 5 stars`}>
              <span className="lm-card__stars">
                {starIcons(rating).map((cls, i) => (
                  <i key={i} className={`bi ${cls}`} />
                ))}
              </span>
              {Number(p.reviewCount) > 0 && <span className="lm-card__rcount">({Number(p.reviewCount)})</span>}
            </span>
          )}
          {sold > 0 && <span className="lm-card__sold">{fmtSold(sold)} sold</span>}
        </div>

        <div className="lm-card__price-row">
          {hasDiscount && overlay !== 'discount' && <span className="lm-card__disc">-{discountPct}%</span>}
          <span className="lm-card__price">
            <span className="lm-card__cur">US&nbsp;$</span>
            {priceInt}
            <sup className="lm-card__cents">{priceCents}</sup>
          </span>
          {hasDiscount && (
            <span className="lm-card__orig">
              List: <s>US&nbsp;${fmtPrice(counter)}</s>
            </span>
          )}
        </div>

        {/* Signals that lost the overlay slot demote to quiet text chips. */}
        {((hasCoupon && overlay !== 'coupon') || (hasGroupon && overlay !== 'groupon')) && (
          <div className="lm-card__chips">
            {hasCoupon && overlay !== 'coupon' && <span className="lm-card__coupon">Coupon</span>}
            {hasGroupon && overlay !== 'groupon' && <span className="lm-card__groupon">Group buy</span>}
          </div>
        )}

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
          <button type="button" className={`lm-card__cart${added ? ' lm-card__cart--added' : ''}`} onClick={handleAddToCart}>
            {added ? 'Added ✓' : 'Add to cart'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default ProductCard;
