import React, { useSyncExternalStore } from 'react';

import { chooseConsent, consentSnapshot, resetConsent, subscribeConsent } from 'app/shared/tracking/consent';

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
  const { reason, choice } = useSyncExternalStore(subscribeConsent, consentSnapshot);

  if (reason === 'pending') {
    return <div className='text-muted small'>Checking analytics settings…</div>;
  }

  if (reason === 'unconfigured') {
    return (
      <div className='alert alert-light border'>
        <i className='bi bi-info-circle me-2' />
        This site has no analytics configured, so no analytics cookies are set and there is nothing to
        consent to.
      </div>
    );
  }

  if (reason === 'dnt') {
    return (
      <div className='alert alert-light border'>
        <i className='bi bi-shield-check me-2 text-success' />
        Your browser sends a <strong>Do Not Track</strong> signal, which we honour: analytics are off and
        we will not ask. To choose for yourself, turn Do Not Track off in your browser settings.
      </div>
    );
  }

  return (
    <div className='border rounded p-3'>
      <div className='fw-semibold mb-1'>Analytics cookies</div>
      <p className='text-muted small mb-3'>
        {choice === 'granted'
          ? 'Currently ON — you have accepted analytics cookies. Withdrawing stops tracking immediately and deletes the cookies already set.'
          : choice === 'denied'
            ? 'Currently OFF — you have declined. Nothing is collected.'
            : 'No choice recorded yet, so nothing is being collected.'}
      </p>
      <div className='d-flex gap-2 flex-wrap'>
        {choice !== 'granted' && (
          <button type='button' className='btn btn-primary btn-sm' onClick={() => chooseConsent('granted')}>
            Accept analytics cookies
          </button>
        )}
        {choice === 'granted' && (
          <button type='button' className='btn btn-outline-danger btn-sm' onClick={() => chooseConsent('denied')}>
            Withdraw consent
          </button>
        )}
        {choice !== null && (
          <button type='button' className='btn btn-link btn-sm text-muted' onClick={resetConsent}>
            Reset choice (ask me again)
          </button>
        )}
      </div>
    </div>
  );
};

export default CookiePreferences;
