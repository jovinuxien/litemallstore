/**
 * Conversion-event facade (Wave 15): one call per commerce milestone, fanned
 * out to every configured tracker (Matomo, Meta Pixel). Both trackers are
 * consent-gated and drop events while unconfigured/undecided/denied — callers
 * never check, and calling with nothing configured is a no-op, exactly like
 * the rest of the tracking seam.
 *
 * <p>Money passed here is DISPLAY money (the cart-lines subtotal, the order's
 * server-computed totals) — analytics only, never anything we charge.
 */

import { fpTrack, numericGoodsId } from 'app/shared/tracking/firstParty';
import { trackCartAdd, trackEvent, trackOrder } from 'app/shared/tracking/matomo';
import { pixelTrack } from 'app/shared/tracking/metaPixel';

/** Storefront display currency — mirrors checkout's STRIPE_CURRENCY (usd). */
const CURRENCY = 'USD';

export interface ProductFacts {
  id: string;
  name: string;
  price: number;
}

export const trackProductView = (p: ProductFacts): void => {
  trackEvent('Ecommerce', 'ProductView', `${p.id} ${p.name}`.trim(), p.price);
  fpTrack('view_item', { goodsId: numericGoodsId(p.id), payload: { price: p.price } });
  pixelTrack('ViewContent', {
    content_ids: [p.id],
    content_type: 'product',
    content_name: p.name,
    value: p.price,
    currency: CURRENCY,
  });
};

export const trackAddToCart = (p: ProductFacts & { quantity: number }, cartTotal: number): void => {
  trackCartAdd(p, cartTotal);
  fpTrack('add_to_cart', { goodsId: numericGoodsId(p.id), payload: { qty: p.quantity, price: p.price } });
  pixelTrack('AddToCart', {
    content_ids: [p.id],
    content_type: 'product',
    content_name: p.name,
    value: p.price * p.quantity,
    currency: CURRENCY,
  });
};

export const trackBeginCheckout = (subtotal: number, itemCount: number): void => {
  trackEvent('Ecommerce', 'BeginCheckout', `${itemCount} items`, subtotal);
  fpTrack('begin_checkout', { payload: { subtotal, itemCount } });
  pixelTrack('InitiateCheckout', { num_items: itemCount, value: subtotal, currency: CURRENCY });
};
// First-party `purchase` is deliberately ABSENT here: it originates server-side
// in litemall-order (doc/behavioral-events.md) — Matomo/Pixel keep their own
// client purchase below for marketing attribution; no double counting.

export interface PurchaseFacts {
  orderId: number;
  revenue: number;
  subtotal?: number;
  tax?: number;
  shipping?: number;
  discount?: number;
}

/**
 * Fires once per order id per browser session — a confirmation-page reload or
 * a re-render with the same fetched detail must not double-count revenue.
 */
export const trackPurchase = (o: PurchaseFacts): void => {
  const key = `lm-purchase-tracked-${o.orderId}`;
  try {
    if (sessionStorage.getItem(key)) return;
    sessionStorage.setItem(key, '1');
  } catch {
    // Storage unavailable (private mode): still track; Matomo's own order-id
    // dedupe limits the damage of a reload.
  }
  trackOrder(o);
  pixelTrack('Purchase', { value: o.revenue, currency: CURRENCY, content_type: 'product' });
};
