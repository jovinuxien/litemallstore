import React from 'react';
import { Link } from 'react-router-dom';

import { Trans, useTranslation } from 'app/i18n';
import { SUPPORT_EMAIL, SUPPORT_HOURS, topFaqEntries } from 'app/modules/static/faqData';
import SocialLinks from 'app/shared/config/SocialLinks';

/**
 * Customer service (Wave-9.1). Real contact channels ONLY — the placeholder
 * phone number is gone for the same honesty rule as payments: never show a
 * channel that does not exist. Top questions come from the shared FAQ data
 * (faqData.ts) and deep-link into /help anchors, so /service and /help
 * cannot drift apart.
 */
const CustomerService: React.FC = () => {
  const { t } = useTranslation('help');
  return (
  <div className='container my-4 lm-doc' style={{ maxWidth: 640 }}>
    <h1 className='mb-3'>{t('service.title')}</h1>

    <ul className='list-group'>
      {/* ONE row, because there is ONE channel. This list previously opened with
          an "Online support" entry carrying these hours and no way to reach it —
          no chat, no phone, not even a link — directly above the email row that
          is the only thing a customer can actually use. Hours belong to the
          channel that answers them. */}
      <li className='list-group-item d-flex align-items-center gap-3'>
        <i className='bi bi-envelope fs-4 text-primary' />
        <div>
          <div className='fw-semibold'>{t('service.emailSupport')}</div>
          <div className='small'>
            <Trans t={t} i18nKey='service.emailLine' values={{ email: SUPPORT_EMAIL }} components={{ 1: <a href={`mailto:${SUPPORT_EMAIL}`} /> }} />
          </div>
          <div className='text-muted small'>{t('service.answered', { hours: SUPPORT_HOURS })}</div>
        </div>
      </li>
    </ul>

    <h2 className='mt-4'>{t('service.topQuestions')}</h2>
    <div className='list-group'>
      {topFaqEntries().map(e => (
        <Link
          key={e.id}
          to={`/help#${e.id}`}
          className='list-group-item list-group-item-action d-flex justify-content-between align-items-center'
        >
          <span>{e.q}</span>
          <i className='bi bi-chevron-right text-muted' aria-hidden='true' />
        </Link>
      ))}
    </div>
    <p className='small mt-2'>
      <Link to='/help'>{t('service.browseAll')}</Link>
    </p>

    <p className='mt-4'>
      <Trans t={t} i18nKey='service.feedback' components={{ 1: <Link to='/user/feedback' /> }} />
    </p>

    <div className='mt-4'>
      <span className='text-uppercase small text-muted d-block mb-2'>{t('service.followUs')}</span>
      <SocialLinks linkClassName='link-secondary' />
    </div>
  </div>
  );
};

export default CustomerService;
