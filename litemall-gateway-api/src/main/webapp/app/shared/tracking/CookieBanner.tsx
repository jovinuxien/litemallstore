import React, { useSyncExternalStore } from 'react';
import { Link } from 'react-router-dom';

import { chooseConsent, consentSnapshot, subscribeConsent } from 'app/shared/tracking/consent';
import { Trans, useTranslation } from 'app/i18n';

/**
 * Opt-in analytics consent banner (Wave-7 Task C).
 *
 * <p>Shown only when there is a real choice to make: no Do-Not-Track and no decision
 * on record. Since behavioral Phase 0 the first-party emitter (firstParty.ts) needs
 * no configuration, so outside DNT there is always a real choice — it upgrades the
 * reason to 'available' even when Matomo/Pixel are unconfigured.
 *
 * <p>Accept and Decline carry equal visual weight, and the banner does not block the
 * page. Consent that is easier to give than to refuse is not freely given, and a
 * banner you must clear to read the site coerces the click.
 *
 * <p>Dismissal is deliberately absent: there is no X. Ignoring the banner leaves the
 * decision un-made, which behaves exactly like a refusal (nothing is tracked) while
 * staying re-askable. Declining is remembered so we stop asking.
 */
const CookieBanner: React.FC = () => {
  const { t } = useTranslation();
  const { reason, choice } = useSyncExternalStore(subscribeConsent, consentSnapshot);

  if (reason !== 'available' || choice !== null) return null;

  return (
    <div
      className='position-fixed bottom-0 start-0 end-0 bg-body border-top shadow-lg p-3'
      style={{ zIndex: 1050 }}
      role='region'
      aria-label={t('cookies.bannerAria')}
    >
      <div className='container d-flex flex-column flex-md-row align-items-md-center gap-3'>
        <div className='small flex-grow-1'>
          <i className='bi bi-shield-check me-2 text-primary' />
          <Trans t={t} i18nKey='cookies.banner' components={{ 1: <Link to='/cookies' /> }} />
        </div>
        <div className='d-flex gap-2 flex-shrink-0'>
          <button type='button' className='btn btn-outline-secondary btn-sm' onClick={() => chooseConsent('denied')}>
            {t('cookies.decline')}
          </button>
          <button type='button' className='btn btn-primary btn-sm' onClick={() => chooseConsent('granted')}>
            {t('cookies.accept')}
          </button>
        </div>
      </div>
    </div>
  );
};

export default CookieBanner;
