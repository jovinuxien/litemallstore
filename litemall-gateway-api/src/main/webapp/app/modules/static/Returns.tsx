import React from 'react';
import { Link } from 'react-router-dom';

import { SELLER } from 'app/modules/static/seller';

/**
 * Returns &amp; Refund Policy (binding copy, Wave 15; 30-day window per owner
 * decision 2026-07-31 — supersedes the old 7-day FAQ claim, which is updated
 * in faqData.ts and the PDP badge in the same change).
 *
 * <p>Routed at `/returns`, NOT `/refunds` — that path is the customer's own
 * refund-request LIST (a protected order view). Keep them distinct.
 */
const Returns: React.FC = () => (
  <div className='container my-4' style={{ maxWidth: 720 }}>
    <h1 className='h4 mb-3'>Returns &amp; Refunds</h1>
    <p className='text-muted small'>Last updated: 31 July 2026</p>

    <h2 className='h6 mt-4'>30-day returns</h2>
    <p className='text-muted small'>
      You can return most items within <strong>30 days of delivery</strong>, for any reason. Items should be
      unused and in their original packaging where possible. Start a request from the order&apos;s after-sales
      section in <Link to='/orders'>My orders</Link>, or email{' '}
      <a href={`mailto:${SELLER.email}`}>{SELLER.email}</a>.
    </p>
    <p className='text-muted small'>
      If you are returning an item because you changed your mind, you pay the return shipping. If the item is
      faulty, damaged, or not what you ordered, we pay it — and you can choose a replacement instead of a
      refund.
    </p>

    <h2 className='h6 mt-4'>Your statutory 14-day withdrawal right (EU/EEA)</h2>
    <p className='text-muted small'>
      If you are a consumer in the EU/EEA you may withdraw from your purchase within 14 days of receiving the
      goods, without giving a reason. Tell us within that period — an after-sales request or an email to{' '}
      <a href={`mailto:${SELLER.email}`}>{SELLER.email}</a> is enough — and return the goods within 14 days of
      telling us. We refund the price and standard delivery cost within 14 days of receiving your withdrawal
      (we may wait until we receive the goods or proof of return). Our 30-day window above is in addition to
      this right, never instead of it.
    </p>

    <h2 className='h6 mt-4'>Faulty items — three-year complaint right</h2>
    <p className='text-muted small'>
      Under Swedish consumer law you can complain about a defective item for three years from delivery. This
      applies regardless of the windows above.
    </p>

    <h2 className='h6 mt-4'>How refunds are paid</h2>
    <p className='text-muted small'>
      Approved refunds go back to how you paid, automatically: a card payment is reversed through Stripe, a
      payment from your wallet balance is credited straight back to the balance. Your bank then usually takes
      a few business days to post a card refund. You can follow progress under{' '}
      <Link to='/refunds'>your refunds</Link>.
    </p>
    <p className='text-muted small'>
      If the payment provider refuses the reversal, your request stays open and visible rather than being
      quietly marked as refunded. We would rather show you an unfinished refund than a finished one where no
      money moved. More on how payments are handled is on <Link to='/payments'>How payments work</Link>.
    </p>

    <h2 className='h6 mt-4'>Help</h2>
    <p className='text-muted small'>
      <Link to='/service'>Customer service</Link> can help with any return. If we cannot resolve a dispute, you
      can turn to Allmänna reklamationsnämnden (arn.se) or the EU dispute-resolution platform — see our{' '}
      <Link to='/terms'>Terms of Sale</Link>.
    </p>
  </div>
);

export default Returns;
