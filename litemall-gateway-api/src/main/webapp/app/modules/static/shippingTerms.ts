/**
 * Shipping and order-lifecycle terms as the customer sees them — ONE definition,
 * the same discipline `seller.ts` applies to the legal identity and `faqData.ts`
 * to support hours.
 *
 * ⚠ THESE MIRROR SERVER CONFIG. They are not the source of truth; the source is
 * the `litemall_system` table, read by litemall-order at checkout:
 *
 *   FREIGHT_FLAT       ← litemall_express_freight_value
 *   FREIGHT_FREE_MIN   ← litemall_express_freight_min
 *   UNPAID_MINUTES     ← litemall_order_unpaid
 *   AUTO_CONFIRM_DAYS  ← litemall_order_unconfirm
 *
 * Nothing public serves those values, so a page that states them has to carry
 * them. Change one in the database and it MUST be changed here in the same
 * breath, or the shipping page starts quoting a price the checkout does not
 * charge — the exact drift that had the footer promising "every day" support
 * against a Mon–Fri page. A read-only shipping-terms endpoint on litemall-order
 * would remove the risk entirely; until one exists, this file is the seam.
 *
 * Values below were read from PRODUCTION on 2026-08-26. Dev disagrees (88 / 8)
 * and is stale — never refresh these from a dev database.
 */

/** Flat shipping charged per order, in EUR. */
export const FREIGHT_FLAT = '6.93';

/** Order subtotal at or above which shipping is free, in EUR. */
export const FREIGHT_FREE_MIN = '76.21';

/** Minutes an unpaid order is held before it is released automatically. */
export const UNPAID_MINUTES = 30;

/** Days after dispatch before an order is confirmed as received automatically. */
export const AUTO_CONFIRM_DAYS = 7;
