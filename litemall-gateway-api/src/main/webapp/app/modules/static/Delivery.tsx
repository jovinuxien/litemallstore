import React from 'react';
import { Link } from 'react-router-dom';

import { Trans, useTranslation } from 'app/i18n';
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
const Delivery: React.FC = () => {
  const { t } = useTranslation('help');
  return (
  <div className='container my-4 lm-doc' style={{ maxWidth: 720 }}>
    <h1 className='mb-3'>{t('delivery.title')}</h1>

    <h2 className='mt-4'>{t('delivery.costTitle')}</h2>
    <p>
      <Trans
        t={t}
        i18nKey='delivery.cost'
        values={{ flat: money(FREIGHT_FLAT), freeMin: money(FREIGHT_FREE_MIN) }}
        components={{ 1: <strong />, 2: <strong />, 3: <strong /> }}
      />
    </p>

    <h2 className='mt-4'>{t('delivery.courierTitle')}</h2>
    <p>{t('delivery.courier')}</p>

    <h2 className='mt-4'>{t('delivery.fromTitle')}</h2>
    <p>
      <Trans t={t} i18nKey='delivery.from1' components={{ 1: <strong /> }} />
    </p>
    <p>{t('delivery.from2')}</p>

    <h2 className='mt-4'>{t('delivery.followTitle')}</h2>
    <ul>
      <li>{t('delivery.follow1')}</li>
      <li>{t('delivery.follow2')}</li>
      <li>
        <Trans t={t} i18nKey='delivery.follow3' components={{ 1: <Link to='/orders' /> }} />
      </li>
      <li>{t('delivery.follow4', { days: AUTO_CONFIRM_DAYS })}</li>
    </ul>

    <h2 className='mt-4'>{t('delivery.addressTitle')}</h2>
    <p>
      <Trans t={t} i18nKey='delivery.address' components={{ 1: <Link to='/orders' />, 2: <Link to='/user/address' /> }} />
    </p>

    <h2 className='mt-4'>{t('delivery.unpaidTitle')}</h2>
    <p>{t('delivery.unpaid', { minutes: UNPAID_MINUTES })}</p>

    <h2 className='mt-4'>{t('delivery.returnsTitle')}</h2>
    <p>
      <Trans t={t} i18nKey='delivery.returns' components={{ 1: <Link to='/returns' />, 2: <Link to='/service' /> }} />
    </p>
  </div>
  );
};

export default Delivery;
