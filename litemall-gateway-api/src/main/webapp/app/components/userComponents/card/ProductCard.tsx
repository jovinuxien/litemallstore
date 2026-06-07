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
  const retail = priceNum(product.retailPrice);
  const counter = priceNum(product.counterPrice);
  const hasDiscount = counter > retail && retail > 0;
  const discountPct = hasDiscount ? Math.round(((counter - retail) / counter) * 100) : 0;
  const sold = Number(product.salesQuantity) || 0;
  const rating = Number(product.star) || 0;
  const to = `/product/${product.id}`;

  // Real add-to-cart needs spec selection, so send the user to the detail page.
  const handleAddToCart = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    navigate(to);
  };

  return (
    <div className="lm-card">
      <Link to={to} className="lm-card__media">
        <img className="lm-card__img" src={product.picUrl} alt={product.name} loading="lazy" />
        {hasDiscount && <span className="lm-card__discount">-{discountPct}%</span>}
        <div className="lm-card__ribbons">
          {product.isNew && <span className="lm-card__ribbon lm-card__ribbon--new">New</span>}
          {product.isHot && <span className="lm-card__ribbon lm-card__ribbon--hot">Hot</span>}
        </div>
      </Link>

      <div className="lm-card__body">
        <Link to={to} className="lm-card__title" title={product.name}>
          {product.name}
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

        {product.isFreeShipping && <div className="lm-card__shipping">🚚 Free shipping</div>}

        <button type="button" className="lm-card__cart" onClick={handleAddToCart}>
          Add to cart
        </button>
      </div>
    </div>
  );
};

export default ProductCard;
