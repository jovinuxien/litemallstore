import React from 'react';
import { Link } from 'react-router-dom';

import LegalPlaceholder from 'app/modules/static/LegalPlaceholder';

/**
 * Terms of Sale (Wave-7 Task D) — destination of the footer's "Conditions of Use",
 * which previously pointed at the FAQ.
 *
 * <p>Headings only, plus factual descriptions of mechanics the code actually
 * implements. The operative terms (liability, warranty, governing law, dispute
 * resolution) are counsel's and are NOT drafted here — those are exactly the clauses
 * where invented text creates real exposure.
 */
const Terms: React.FC = () => (
  <div className='container my-4' style={{ maxWidth: 720 }}>
    <h1 className='h4 mb-3'>Terms of Sale</h1>
    <LegalPlaceholder page='terms of sale' />

    <h2 className='h6 mt-4'>Ordering</h2>
    <p className='text-muted small'>
      Placing an order is an offer to buy. Prices and availability are confirmed by our server when you check
      out, so the amount charged is the amount shown at checkout — including any tax calculated for your
      delivery address.
    </p>

    <h2 className='h6 mt-4'>Payment</h2>
    <p className='text-muted small'>
      We accept credit/debit cards (processed by Stripe) and your wallet balance. Card details are entered
      directly with Stripe and never reach our servers.
    </p>

    <h2 className='h6 mt-4'>Delivery</h2>
    <p className='text-muted small'>
      Some items ship directly from our dropshipping partner and may arrive separately from other items in the
      same order. Delivery estimates are shown at checkout.
    </p>

    <h2 className='h6 mt-4'>Returns and cancellation</h2>
    <p className='text-muted small'>
      See our <Link to='/returns'>Returns &amp; Refund Policy</Link>. Statutory rights, including any
      applicable cooling-off period, are unaffected.
    </p>

    <h2 className='h6 mt-4'>Liability, warranties and governing law</h2>
    <p className='text-muted small'>
      <em>To be supplied by counsel — see the draft notice above.</em>
    </p>

    <h2 className='h6 mt-4'>Contact</h2>
    <p className='text-muted small'>
      <Link to='/service'>Customer service</Link>.
    </p>
  </div>
);

export default Terms;
