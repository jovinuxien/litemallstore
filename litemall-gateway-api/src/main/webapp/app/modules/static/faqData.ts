/**
 * Shared FAQ content (Wave-9.1), consumed by BOTH `/help` (full hub) and
 * `/service` (top questions) so the two surfaces cannot drift.
 *
 * Honesty rule (same as payments): every answer states only what the store
 * actually does — card via Stripe + wallet balance, the 30-day return window
 * from Returns.tsx, order actions that exist in OrderList/OrderDetail
 * (server-driven handleOption), honest dropshipping delivery framing. No
 * invented policies, no invented contact channels.
 *
 * Answers are plain strings (not JSX) so the /help filter can search them;
 * related in-app destinations ride along as typed links.
 */

import { t } from 'app/i18n';

import { FREIGHT_FLAT, FREIGHT_FREE_MIN } from './shippingTerms';

export const SUPPORT_EMAIL = 'support@trovemo.com';

/**
 * When email is actually answered — ONE definition, because the footer's
 * customer-promise strip and /service each carried their own and disagreed:
 * the footer claimed "Customer care, every day" while /service said Mon–Fri.
 * Anything that states support hours reads this.
 */
export const SUPPORT_HOURS = 'Mon–Fri, 9:00–18:00';

export interface FaqLink {
  label: string;
  /** In-app route (react-router `to`). */
  to: string;
}

export interface FaqEntry {
  /** Stable id — also the DOM anchor, so /service can deep-link /help#<id>. */
  id: string;
  q: string;
  a: string;
  links?: FaqLink[];
  /** Shown in the "Top questions" list on /service. */
  top?: boolean;
}

export interface FaqSection {
  /** Stable id — DOM anchor for /help#<id> section links. */
  id: string;
  title: string;
  icon: string;
  entries: FaqEntry[];
}

interface FaqLinkSpec {
  labelKey: string;
  to: string;
}
interface FaqEntrySpec {
  id: string;
  links?: FaqLinkSpec[];
  top?: boolean;
}
interface FaqSectionSpec {
  id: string;
  icon: string;
  entries: FaqEntrySpec[];
}

/**
 * The STRUCTURE of the FAQ — ids (also the /help#anchor and the PDP subset key),
 * icons, link targets and the "top" flag. The wording lives in
 * i18n/locales/<lang>/help.json under sections.<id>, faq.<id>.q/a and links.<key>,
 * so /help, /service and the PDP "Common questions" all follow the locale from one
 * source. Money facts are interpolated from shippingTerms.ts, never retyped.
 */
const FAQ_SPEC: FaqSectionSpec[] = [
  {
    id: 'orders',
    icon: 'bi-box-seam',
    entries: [
      { id: 'place-order', links: [{ labelKey: 'cart', to: '/cart' }] },
      { id: 'track-order', links: [{ labelKey: 'orders', to: '/orders' }], top: true },
      { id: 'delivery-time', links: [{ labelKey: 'orders', to: '/orders' }], top: true },
      { id: 'shipping-cost', links: [{ labelKey: 'delivery', to: '/delivery' }], top: true },
      { id: 'eu-stock', links: [{ labelKey: 'delivery', to: '/delivery' }] },
      { id: 'cancel-order', links: [{ labelKey: 'orders', to: '/orders' }] },
      {
        id: 'change-address',
        links: [
          { labelKey: 'orders', to: '/orders' },
          { labelKey: 'addresses', to: '/user/address' },
        ],
      },
    ],
  },
  {
    id: 'payments',
    icon: 'bi-credit-card',
    entries: [
      { id: 'payment-methods', top: true },
      { id: 'card-safety', links: [{ labelKey: 'payments', to: '/payments' }] },
      { id: 'payment-failed', links: [{ labelKey: 'orders', to: '/orders' }] },
      { id: 'when-charged' },
    ],
  },
  {
    id: 'returns',
    icon: 'bi-arrow-counterclockwise',
    entries: [
      {
        id: 'return-window',
        links: [
          { labelKey: 'returnsPolicy', to: '/returns' },
          { labelKey: 'orders', to: '/orders' },
        ],
        top: true,
      },
      { id: 'refund-method', links: [{ labelKey: 'refunds', to: '/refunds' }] },
      { id: 'refund-time', links: [{ labelKey: 'refunds', to: '/refunds' }] },
    ],
  },
  {
    id: 'account',
    icon: 'bi-shield-lock',
    entries: [
      { id: 'reset-password', links: [{ labelKey: 'signIn', to: '/login' }], top: true },
      {
        id: 'update-profile',
        links: [
          { labelKey: 'profile', to: '/user/profile' },
          { labelKey: 'addresses', to: '/user/address' },
        ],
      },
      {
        id: 'privacy-cookies',
        links: [
          { labelKey: 'privacy', to: '/privacy' },
          { labelKey: 'cookies', to: '/cookies' },
        ],
      },
    ],
  },
  {
    id: 'coupons',
    icon: 'bi-ticket-perforated',
    entries: [
      {
        id: 'use-coupon',
        links: [
          { labelKey: 'couponCenter', to: '/coupons' },
          { labelKey: 'myCoupons', to: '/user/coupons' },
        ],
        top: true,
      },
      { id: 'coupon-not-applying', links: [{ labelKey: 'myCoupons', to: '/user/coupons' }] },
      {
        id: 'find-deals',
        links: [
          { labelKey: 'deals', to: '/deals' },
          { labelKey: 'hot', to: '/hot' },
          { labelKey: 'groupBuys', to: '/groupon' },
        ],
      },
    ],
  },
];

/** The FAQ resolved in the CURRENT locale. Call it at render time (components that
 *  subscribe with useTranslation re-render on a switch and pick the new text up). */
export const faqSections = (): FaqSection[] =>
  FAQ_SPEC.map(s => ({
    id: s.id,
    icon: s.icon,
    title: t(`help:sections.${s.id}`),
    entries: s.entries.map(e => ({
      id: e.id,
      q: t(`help:faq.${e.id}.q`),
      a: t(`help:faq.${e.id}.a`, { flat: FREIGHT_FLAT, freeMin: FREIGHT_FREE_MIN }),
      links: e.links?.map(l => ({ label: t(`help:links.${l.labelKey}`), to: l.to })),
      top: e.top,
    })),
  }));

export const topFaqEntries = (): FaqEntry[] => faqSections().flatMap(s => s.entries.filter(e => e.top));
