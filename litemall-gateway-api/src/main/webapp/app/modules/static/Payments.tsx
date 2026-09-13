import React from 'react';
import { Link } from 'react-router-dom';

import { Trans, useTranslation } from 'app/i18n';

/**
 * How payments work — the page behind the footer's "Secure payments" promise.
 *
 * Every claim here is checkable in litemall-order:
 *  - Stripe PaymentIntent with automatic payment methods (so SCA / 3-D Secure is
 *    handled on that path), charged in the configured currency (eur in prod).
 *  - StripePaymentGatewayAdapter.verify() asserts status == "succeeded", that
 *    amount_received equals the expected minor units exactly, and that the
 *    intent's orderId metadata matches — with NO fallback, because "a stub id
 *    here is indistinguishable from a real payment later".
 *  - Refunds: adapter .refund() -> Stripe Refund.create with an idempotency key;
 *    a provider rejection throws and rolls back, leaving the order in
 *    REFUND_REQUEST rather than falsely reading REFUNDED.
 *
 * ⚠ NO payment method is named beyond card and store balance. The adapter enables
 * Stripe's automatic payment methods, so whichever methods are switched on in the
 * Stripe Dashboard appear at checkout — that is a dashboard fact this codebase
 * cannot see. Naming Klarna/iDEAL/SEPA here without confirming them would be the
 * same unverified promise the footer used to make.
 */
const Payments: React.FC = () => {
  const { t } = useTranslation('help');
  return (
  <div className='container my-4 lm-doc' style={{ maxWidth: 720 }}>
    <h1 className='mb-3'>{t('payments.title')}</h1>

    <h2 className='mt-4'>{t('payments.whoTitle')}</h2>
    <p>
      <Trans t={t} i18nKey='payments.who' components={{ 1: <strong /> }} />
    </p>

    <h2 className='mt-4'>{t('payments.bankTitle')}</h2>
    <p>{t('payments.bank')}</p>

    <h2 className='mt-4'>{t('payments.chargedTitle')}</h2>
    <p>
      <Trans t={t} i18nKey='payments.charged' components={{ 1: <strong />, 2: <Link to='/delivery' /> }} />
    </p>

    <h2 className='mt-4'>{t('payments.verifyTitle')}</h2>
    <p>
      <Trans t={t} i18nKey='payments.verify' components={{ 1: <strong /> }} />
    </p>

    <h2 className='mt-4'>{t('payments.balanceTitle')}</h2>
    <p>{t('payments.balance')}</p>

    <h2 className='mt-4'>{t('payments.refundsTitle')}</h2>
    <p>
      <Trans t={t} i18nKey='payments.refunds' components={{ 1: <Link to='/returns' /> }} />
    </p>

    <h2 className='mt-4'>{t('payments.keepTitle')}</h2>
    <p>
      <Trans t={t} i18nKey='payments.keep' components={{ 1: <Link to='/privacy' /> }} />
    </p>

    <h2 className='mt-4'>{t('payments.wrongTitle')}</h2>
    <p>
      <Trans t={t} i18nKey='payments.wrong' components={{ 1: <Link to='/service' /> }} />
    </p>
  </div>
  );
};

export default Payments;
