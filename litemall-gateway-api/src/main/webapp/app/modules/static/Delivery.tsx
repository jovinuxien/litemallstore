import React from 'react';
import { Link } from 'react-router-dom';

import { money } from 'app/shared/util/money';

import { AUTO_CONFIRM_DAYS, FREIGHT_FLAT, FREIGHT_FREE_MIN, UNPAID_MINUTES } from './shippingTerms';

/**
 * Shipping &amp; delivery — the page behind the footer's "Tracked delivery" promise.
 *
 * Written from what the system actually does, because that promise used to read
 * "Fast, tracked delivery / on every order, nationwide" on a cross-border store
 * whose own FAQ admits most stock ships from a supplier warehouse.
 *
 * NO DELIVERY-TIME ESTIMATE APPEARS HERE, deliberately: we have no measured
 * transit data, and a range nobody measured is the same failure in a new place.
 * If real numbers ever come out of the order history, this is where they go.
 *
 * Prices and windows come from shippingTerms.ts, which documents the
 * `litemall_system` keys they mirror.
 */
const Delivery: React.FC = () => (
  <div className='container my-4' style={{ maxWidth: 720 }}>
    <h1 className='h4 mb-3'>Shipping &amp; delivery</h1>

    <h2 className='h6 mt-4'>What it costs</h2>
    <p className='text-muted small'>
      Shipping is a flat <strong>{money(FREIGHT_FLAT)}</strong> per order. Orders of{' '}
      <strong>{money(FREIGHT_FREE_MIN)}</strong> or more ship <strong>free</strong> — it comes off
      automatically at checkout, with no code to enter.
    </p>

    <h2 className='h6 mt-4'>Choosing a courier</h2>
    <p className='text-muted small'>
      At checkout you will see the delivery options available for your basket. The standard option is
      included in the flat rate. A faster courier shows exactly what it adds — &ldquo;+€3.20&rdquo;, for
      instance — and nothing is added unless you choose it. The price you are shown is the price you pay:
      we recalculate it on our own servers when the order is placed, never from your browser.
    </p>

    <h2 className='h6 mt-4'>Where your order ships from</h2>
    <p className='text-muted small'>
      Most items ship from our supplier&apos;s warehouse. Some are held in an EU warehouse, and those carry
      an <strong>EU stock</strong> label on the product card and the product page. That label reflects our
      most recent stock check rather than a promise about your particular parcel, and an item without it is
      not necessarily slower — we simply have no recent reading for it.
    </p>
    <p className='text-muted small'>
      Everything else travels further, so allow more time than a domestic delivery. We would rather say that
      plainly than quote a delivery date we cannot stand behind.
    </p>

    <h2 className='h6 mt-4'>Following your order</h2>
    <ul className='text-muted small'>
      <li>When your order is dispatched we email you the carrier and tracking number.</li>
      <li>
        If the carrier has not issued a number yet, we tell you so and follow up — you will never get an
        email with a blank tracking line.
      </li>
      <li>
        Every order has a live status page under <Link to='/orders'>Your orders</Link>, showing tracking
        events as the carrier reports them.
      </li>
      <li>
        An order is marked as received automatically {AUTO_CONFIRM_DAYS} days after dispatch. You can
        confirm sooner from the order page.
      </li>
    </ul>

    <h2 className='h6 mt-4'>Changing your address</h2>
    <p className='text-muted small'>
      A delivery address cannot be edited once an order is placed. If the order can still be cancelled,
      cancel it under <Link to='/orders'>Your orders</Link> and place it again with the right address.
      Keeping your <Link to='/user/address'>address book</Link> current is the easiest way to avoid this.
    </p>

    <h2 className='h6 mt-4'>Orders that are not paid</h2>
    <p className='text-muted small'>
      An order left unpaid for {UNPAID_MINUTES} minutes is released automatically and the items return to
      stock. Nothing is charged.
    </p>

    <h2 className='h6 mt-4'>Returns</h2>
    <p className='text-muted small'>
      You have 30 days to change your mind — see <Link to='/returns'>Returns &amp; Refunds</Link> for the
      full policy, including your statutory withdrawal right. Anything else, please{' '}
      <Link to='/service'>get in touch</Link>.
    </p>
  </div>
);

export default Delivery;
