import React, { useSyncExternalStore } from 'react';

import { chooseConsent, consentSnapshot, resetConsent, subscribeConsent } from 'app/shared/tracking/consent';
import { Trans, useTranslation } from 'app/i18n';

/**
 * The withdrawal path required by Task C: consent that cannot be taken back is not
 * consent. Embedded in the Cookie Policy page (`/cookies`), which is where the footer
 * "Cookie Preferences" link points.
 *
 * <p>It states why tracking is off when the user's choice is not the reason — a
 * deployment without Matomo configured, or a browser sending Do-Not-Track, are both
 * outside their control here and saying "you have declined" would be a lie.
 */
const CookiePreferences: React.FC = () => {
  const { t } = useTranslation();
  const { reason, choice } = useSyncExternalStore(subscribeConsent, consentSnapshot);

  if (reason === 'pending') {
    return <div className='text-muted small'>{t('cookies.checking')}</div>;
  }

  if (reason === 'unconfigured') {
    return (
      <div className='alert alert-light border'>
        <i className='bi bi-info-circle me-2' />
        {t('cookies.noneConfigured')}
      </div>
    );
  }

  if (reason === 'dnt') {
    return (
      <div className='alert alert-light border'>
        <i className='bi bi-shield-check me-2 text-success' />
        <Trans t={t} i18nKey='cookies.dnt' components={{ 1: <strong /> }} />
      </div>
    );
  }

  return (
    <div className='border rounded p-3'>
      <div className='fw-semibold mb-1'>{t('cookies.prefsTitle')}</div>
      <p className='text-muted small mb-3'>
        {choice === 'granted' ? t('cookies.statusOn') : choice === 'denied' ? t('cookies.statusOff') : t('cookies.statusNone')}
      </p>
      <div className='d-flex gap-2 flex-wrap'>
        {choice !== 'granted' && (
          <button type='button' className='btn btn-primary btn-sm' onClick={() => chooseConsent('granted')}>
            {t('cookies.acceptAll')}
          </button>
        )}
        {choice === 'granted' && (
          <button type='button' className='btn btn-outline-danger btn-sm' onClick={() => chooseConsent('denied')}>
            {t('cookies.withdraw')}
          </button>
        )}
        {choice !== null && (
          <button type='button' className='btn btn-link btn-sm text-muted' onClick={resetConsent}>
            {t('cookies.reset')}
          </button>
        )}
      </div>
    </div>
  );
};

export default CookiePreferences;
