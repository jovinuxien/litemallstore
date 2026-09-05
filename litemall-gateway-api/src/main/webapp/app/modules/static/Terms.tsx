import React from 'react';
import { Link } from 'react-router-dom';

import { SELLER } from 'app/modules/static/seller';

/**
 * Terms of Sale (binding copy, Wave 15; structure from Wave-7 Task D). The
 * factual mechanics sections describe what the code actually does — keep them
 * in sync with the checkout when it changes.
 */
const Terms: React.FC = () => (
  <div className='container my-4 lm-doc' style={{ maxWidth: 720 }}>
    <h1 className='mb-3'>Terms of Sale</h1>
    <p className='text-muted small'>Last updated: 31 July 2026</p>

    <h2 className='mt-4'>Who we are</h2>
    <p>
      {SELLER.storeName} (trovemo.com) is operated by <strong>{SELLER.name}</strong>, {SELLER.address}. You can
      reach us at <a href={`mailto:${SELLER.email}`}>{SELLER.email}</a> or via{' '}
      <Link to='/service'>customer service</Link>. These terms apply to every order placed on trovemo.com; by
      placing an order you accept them. Nothing in them limits rights you have under mandatory consumer law.
    </p>

    <h2 className='mt-4'>Ordering</h2>
    <p>
      Placing an order is an offer to buy. Prices and availability are confirmed by our server when you check
      out, so the amount charged is the amount shown at checkout — including any tax calculated for your
      delivery address. The contract is concluded when we confirm the order after successful payment. We may
      refuse or cancel an order affected by an obvious pricing or listing error; if we cancel, you are refunded
      in full.
    </p>

    <h2 className='mt-4'>Payment</h2>
    <p>
      We accept credit/debit cards (processed by Stripe) and your wallet balance. Card details are entered
      directly with Stripe and never reach our servers. All prices are shown in euros (EUR).
    </p>

    <h2 className='mt-4'>Delivery</h2>
    <p>
      Some items ship directly from our dropshipping partner and may arrive separately from other items in the
      same order. Delivery estimates shown at checkout are estimates, not guarantees; direct-shipped items can
      take longer. If an order has not arrived within 30 days of the latest estimate, contact us and we will
      trace, replace, or refund it. Risk in the goods passes to you on delivery.
    </p>

    <h2 className='mt-4'>Returns and cancellation</h2>
    <p>
      You have a 30-day return window, and — if you are a consumer in the EU/EEA — a statutory 14-day right of
      withdrawal. Both are described in our <Link to='/returns'>Returns &amp; Refund Policy</Link>, which forms
      part of these terms. Statutory rights are unaffected.
    </p>

    <h2 className='mt-4'>Faulty goods and warranty</h2>
    <p>
      If an item is faulty, not as described, or damaged in transit, we will repair, replace, or refund it at
      no cost to you. As a consumer buying from a Swedish seller you have a statutory right to complain about
      defects for three years from delivery (konsumentköplagen). This sits alongside — and is not limited by —
      the 30-day return window.
    </p>

    <h2 className='mt-4'>Liability</h2>
    <p>
      We are liable under applicable law for defective goods, for refunds due, and for damage caused by our
      negligence. To the extent permitted by mandatory law, we are not liable for indirect or consequential
      losses, and our total liability for any order is limited to the amount you paid for that order. Nothing
      in these terms excludes liability that cannot be excluded by law, including for death or personal injury
      caused by negligence or for fraud.
    </p>

    <h2 className='mt-4'>Governing law and disputes</h2>
    <p>
      These terms are governed by the law of {SELLER.country}. Disputes may be brought before the{' '}
      {SELLER.court}; if you are a consumer, you always keep the protections and forum rights of the mandatory
      law of the country where you live. Consumers may also use the Swedish National Board for Consumer
      Disputes (Allmänna reklamationsnämnden, arn.se) or the EU online dispute-resolution platform at{' '}
      <a href='https://ec.europa.eu/consumers/odr' target='_blank' rel='noreferrer'>
        ec.europa.eu/consumers/odr
      </a>
      . We participate in ARN proceedings.
    </p>

    <h2 className='mt-4'>Contact</h2>
    <p>
      <a href={`mailto:${SELLER.email}`}>{SELLER.email}</a> · <Link to='/service'>Customer service</Link>
    </p>
  </div>
);

export default Terms;
