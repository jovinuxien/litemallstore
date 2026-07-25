/**
 * Shared FAQ content (Wave-9.1), consumed by BOTH `/help` (full hub) and
 * `/service` (top questions) so the two surfaces cannot drift.
 *
 * Honesty rule (same as payments): every answer states only what the store
 * actually does — card via Stripe + wallet balance, the 7-day return window
 * from Returns.tsx, order actions that exist in OrderList/OrderDetail
 * (server-driven handleOption), honest dropshipping delivery framing. No
 * invented policies, no invented contact channels.
 *
 * Answers are plain strings (not JSX) so the /help filter can search them;
 * related in-app destinations ride along as typed links.
 */

export const SUPPORT_EMAIL = 'support@trovemo.com';

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

export const FAQ_SECTIONS: FaqSection[] = [
  {
    id: 'orders',
    title: 'Orders & Delivery',
    icon: 'bi-box-seam',
    entries: [
      {
        id: 'place-order',
        q: 'How do I place an order?',
        a: 'Add items to your cart, then go to checkout, choose a delivery address and a payment method, and place the order.',
        links: [{ label: 'Your cart', to: '/cart' }],
      },
      {
        id: 'track-order',
        q: 'How do I track my order?',
        a: 'Open My orders from the account menu. Each order has a status timeline, and tracking details appear on the order page once it ships.',
        links: [{ label: 'My orders', to: '/orders' }],
        top: true,
      },
      {
        id: 'delivery-time',
        q: 'How long does delivery take?',
        a: 'Many of our products ship directly from our suppliers’ warehouses, which can be overseas, so delivery times vary by product and destination and can be longer than domestic shipping. Follow each order’s progress on its status timeline in My orders.',
        links: [{ label: 'My orders', to: '/orders' }],
        top: true,
      },
      {
        id: 'cancel-order',
        q: 'Can I cancel an order?',
        a: 'While an order can still be cancelled (typically before it is paid), a Cancel button is shown on it in My orders. After payment, use the order’s refund and after-sales options instead.',
        links: [{ label: 'My orders', to: '/orders' }],
      },
      {
        id: 'change-address',
        q: 'Can I change the delivery address after ordering?',
        a: 'An order’s delivery address cannot be edited after it is placed. If the order can still be cancelled, cancel it in My orders and place it again with the right address. Keep your address book up to date to avoid this.',
        links: [
          { label: 'My orders', to: '/orders' },
          { label: 'Your addresses', to: '/user/address' },
        ],
      },
    ],
  },
  {
    id: 'payments',
    title: 'Payments & Pricing',
    icon: 'bi-credit-card',
    entries: [
      {
        id: 'payment-methods',
        q: 'Which payment methods are supported?',
        a: 'Credit or debit card, processed securely by Stripe, and your wallet balance. Card details are entered in Stripe’s payment form and never touch our servers.',
        top: true,
      },
      {
        id: 'payment-failed',
        q: 'My payment failed — what now?',
        a: 'Nothing is lost: the order is kept as unpaid. Open it in My orders and use Pay to try again — you can retry the card or switch payment method.',
        links: [{ label: 'My orders', to: '/orders' }],
      },
      {
        id: 'when-charged',
        q: 'When am I charged?',
        a: 'When you complete payment for an order — either right at checkout or later from the order’s Pay button. Placing an order without paying does not charge you.',
      },
    ],
  },
  {
    id: 'returns',
    title: 'Returns & Refunds',
    icon: 'bi-arrow-counterclockwise',
    entries: [
      {
        id: 'return-window',
        q: 'What is the return policy?',
        a: 'Most items can be returned within 7 days of delivery. Start a request from the order’s after-sales section in My orders. The full policy is on the Returns & Refunds page.',
        links: [
          { label: 'Returns & Refunds policy', to: '/returns' },
          { label: 'My orders', to: '/orders' },
        ],
        top: true,
      },
      {
        id: 'refund-method',
        q: 'How are refunds paid?',
        a: 'Back to how you paid: card payments are reversed via Stripe, wallet payments return to your wallet balance. Follow progress under Your refunds.',
        links: [{ label: 'Your refunds', to: '/refunds' }],
      },
      {
        id: 'refund-time',
        q: 'How long does a refund take?',
        a: 'Wallet refunds show in your balance once the refund is approved. Card refunds are issued to Stripe on approval; your bank then usually takes a few business days to post the money.',
        links: [{ label: 'Your refunds', to: '/refunds' }],
      },
    ],
  },
  {
    id: 'account',
    title: 'Account & Security',
    icon: 'bi-shield-lock',
    entries: [
      {
        id: 'reset-password',
        q: 'I forgot my password — how do I reset it?',
        a: 'Use the Forgot password option on the sign-in page to reset it by email. If that option is not shown, email us from the address on your account and we’ll help.',
        links: [{ label: 'Sign in', to: '/login' }],
        top: true,
      },
      {
        id: 'update-profile',
        q: 'How do I update my profile or addresses?',
        a: 'Your profile (name, password) lives under Account, and delivery addresses under Your addresses.',
        links: [
          { label: 'Profile', to: '/user/profile' },
          { label: 'Your addresses', to: '/user/address' },
        ],
      },
      {
        id: 'privacy-cookies',
        q: 'How is my data used, and how do I manage cookies?',
        a: 'The Privacy Notice explains what we collect and why. Optional analytics cookies are off unless you accept them, and you can change your choice any time under Cookie Preferences.',
        links: [
          { label: 'Privacy Notice', to: '/privacy' },
          { label: 'Cookie Preferences', to: '/cookies' },
        ],
      },
    ],
  },
  {
    id: 'coupons',
    title: 'Coupons & Deals',
    icon: 'bi-ticket-perforated',
    entries: [
      {
        id: 'use-coupon',
        q: 'How do I use a coupon?',
        a: 'Available coupons appear at checkout and on eligible product pages — pick one to apply the discount. Coupons you have collected are listed under Coupons & rewards.',
        links: [{ label: 'Coupons & rewards', to: '/user/coupons' }],
        top: true,
      },
      {
        id: 'coupon-not-applying',
        q: 'Why isn’t my coupon applying?',
        a: 'Coupons have a validity window, and many have a minimum spend or only apply to certain items. Check the coupon’s conditions under Coupons & rewards and make sure the cart qualifies.',
        links: [{ label: 'Coupons & rewards', to: '/user/coupons' }],
      },
      {
        id: 'find-deals',
        q: 'Where do I find current deals?',
        a: 'Today’s deals collects time-limited offers, Hot deals shows what’s trending, and Group buys unlock a lower price when enough buyers join.',
        links: [
          { label: 'Today’s deals', to: '/deals' },
          { label: 'Hot deals', to: '/hot' },
          { label: 'Group buys', to: '/groupon' },
        ],
      },
    ],
  },
];

/** Entries flagged for the /service "Top questions" list, in section order. */
export const topFaqEntries = (): FaqEntry[] =>
  FAQ_SECTIONS.flatMap(s => s.entries.filter(e => e.top));
