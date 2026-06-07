import React from 'react';
import { Link, useNavigate } from 'react-router-dom';

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

interface Props {
  product: IGood;
}

/**
 * Storefront product card (Teal & Coral theme). Layout mirrors a marketplace
 * listing card; colors come from the CSS variables in product-card.scss.
 */
const ProductCard: React.FC<Props> = ({ product }) => {
  const navigate = useNavigate();
  // The backend goods DTO uses goodsName / goodsId:{id} / new / hot, while IGood
  // types them as name / id / isNew / isHot. Read whichever is present so cards
  // work against both the OCS search shape and the home/list payloads.
  const p = product as IGood & {
    goodsId?: { id?: number } | number;
    goodsName?: string;
    new?: boolean;
    hot?: boolean;
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

  // Real add-to-cart needs spec selection, so send the user to the detail page.
  const handleAddToCart = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    navigate(to);
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

        <div className="lm-card__meta">
          {rating > 0 && (
            <span className="lm-card__rating">
              <span className="lm-card__star">★</span>
              {rating.toFixed(1)}
            </span>
          )}
          {sold > 0 && <span className="lm-card__sold">{fmtSold(sold)} sold</span>}
        </div>

        {p.isFreeShipping && <div className="lm-card__shipping">🚚 Free shipping</div>}

        <button type="button" className="lm-card__cart" onClick={handleAddToCart}>
          Add to cart
        </button>
      </div>
    </div>
  );
};

export default ProductCard;
