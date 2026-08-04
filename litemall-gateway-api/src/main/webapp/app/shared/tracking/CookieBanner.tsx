import React, { useSyncExternalStore } from 'react';
import { Link } from 'react-router-dom';

import { chooseConsent, consentSnapshot, subscribeConsent } from 'app/shared/tracking/consent';

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
  const { reason, choice } = useSyncExternalStore(subscribeConsent, consentSnapshot);

  if (reason !== 'available' || choice !== null) return null;

  return (
    <div
      className='position-fixed bottom-0 start-0 end-0 bg-body border-top shadow-lg p-3'
      style={{ zIndex: 1050 }}
      role='region'
      aria-label='Cookie consent'
    >
      <div className='container d-flex flex-column flex-md-row align-items-md-center gap-3'>
        <div className='small flex-grow-1'>
          <i className='bi bi-shield-check me-2 text-primary' />
          We&apos;d like to use analytics and marketing cookies to understand how the store is used and
          measure our advertising. They are optional — decline and nothing is collected. See our{' '}
          <Link to='/cookies'>Cookie Policy</Link>.
        </div>
        <div className='d-flex gap-2 flex-shrink-0'>
          <button type='button' className='btn btn-outline-secondary btn-sm' onClick={() => chooseConsent('denied')}>
            Decline
          </button>
          <button type='button' className='btn btn-primary btn-sm' onClick={() => chooseConsent('granted')}>
            Accept
          </button>
        </div>
      </div>
    </div>
  );
};

export default CookieBanner;
