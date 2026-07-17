import React from 'react';
import { Link } from 'react-router-dom';

import LegalPlaceholder from 'app/modules/static/LegalPlaceholder';

/**
 * Returns &amp; Refund Policy (Wave-7 Task D).
 *
 * <p>Routed at `/returns`, NOT `/refunds` — that path is already the customer's
 * refund-request LIST (a protected order view). A policy page and a list of your own
 * refunds are different things and must not collide.
 *
 * <p>The 7-day window below matches what the Help FAQ has always claimed; it is
 * repeated rather than invented. Whether it survives counsel review (EU distance
 * selling mandates a 14-day withdrawal right, and this store is launching into the
 * EU) is exactly the sort of question flagged in the launch-blocker doc — engineering
 * should not settle it.
 */
const Returns: React.FC = () => (
  <div className='container my-4' style={{ maxWidth: 720 }}>
    <h1 className='h4 mb-3'>Returns &amp; Refunds</h1>
    <LegalPlaceholder page='returns policy' />

    <h2 className='h6 mt-4'>Returning an item</h2>
    <p className='text-muted small'>
      Most items can be returned within 7 days of delivery. Start a request from the order&apos;s after-sales
      section in <Link to='/orders'>My orders</Link>.
      {/* Consistency note: this window is copied from the long-standing Help FAQ.
          Counsel must confirm it against the EU 14-day withdrawal right before launch
          — see docs/LAUNCH-BLOCKER-legal-copy.md. */}
    </p>

    <h2 className='h6 mt-4'>How refunds are paid</h2>
    <p className='text-muted small'>
      Approved refunds go back to how you paid: card payments are reversed via Stripe, wallet payments return
      to your wallet balance. You can follow progress under <Link to='/refunds'>your refunds</Link>.
    </p>

    <h2 className='h6 mt-4'>Statutory withdrawal rights</h2>
    <p className='text-muted small'>
      <em>To be supplied by counsel — see the draft notice above.</em>
    </p>

    <h2 className='h6 mt-4'>Help</h2>
    <p className='text-muted small'>
      <Link to='/service'>Customer service</Link> can help with any return.
    </p>
  </div>
);

export default Returns;
