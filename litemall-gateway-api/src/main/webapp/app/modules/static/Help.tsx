import React, { useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { Trans, useTranslation } from 'app/i18n';
import { faqSections, FaqEntry, SUPPORT_EMAIL } from 'app/modules/static/faqData';

/**
 * Help center (Wave-9.1): the 5-entry flat FAQ grew into a self-service hub.
 * Content lives in faqData.ts, shared with /service so the two cannot drift.
 * Structure: filter box → topic sections (anchored, so /service and the
 * "still stuck?" card can deep-link /help#<id>) → guided escalation, self-serve
 * links first, support@trovemo.com + /user/feedback last.
 */

const matches = (e: FaqEntry, needle: string): boolean =>
  e.q.toLowerCase().includes(needle) || e.a.toLowerCase().includes(needle);

const Entry: React.FC<{ entry: FaqEntry }> = ({ entry }) => (
  <div id={entry.id} className='border-bottom py-3'>
    <div className='fw-semibold mb-1'>
      <i className='bi bi-question-circle me-2 text-primary' />
      {entry.q}
    </div>
    <div>{entry.a}</div>
    {entry.links && entry.links.length > 0 && (
      <div className='small mt-1'>
        {entry.links.map((l, i) => (
          <React.Fragment key={l.to}>
            {i > 0 && <span className='text-muted mx-1'>·</span>}
            <Link to={l.to}>{l.label}</Link>
          </React.Fragment>
        ))}
      </div>
    )}
  </div>
);

const Help: React.FC = () => {
  const { t, i18n } = useTranslation('help');
  const [filter, setFilter] = useState('');
  const { hash } = useLocation();

  // React Router doesn't scroll to #anchors on its own; do it once the
  // sections are in the DOM. Covers /service → /help#returns deep links.
  useEffect(() => {
    if (!hash) return;
    document.getElementById(hash.slice(1))?.scrollIntoView();
  }, [hash]);

  const needle = filter.trim().toLowerCase();
  const sections = useMemo(
    () =>
      faqSections()
        .map(s => ({
          ...s,
          entries: needle ? s.entries.filter(e => matches(e, needle)) : s.entries,
        }))
        .filter(s => s.entries.length > 0),
    // i18n.language is a dependency on purpose: the FAQ text is resolved per locale.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [needle, i18n.language]
  );

  return (
    <div className='container my-4 lm-doc' style={{ maxWidth: 720 }}>
      <h1 className='mb-3'>{t('center.title')}</h1>

      <div className='input-group mb-4'>
        <span className='input-group-text'>
          <i className='bi bi-search' aria-hidden='true' />
        </span>
        <input
          type='search'
          className='form-control'
          placeholder={t('center.searchPlaceholder')}
          aria-label={t('center.searchAria')}
          value={filter}
          onChange={e => setFilter(e.target.value)}
        />
      </div>

      {sections.length === 0 && (
        <p>{t('center.noMatch', { query: filter })}</p>
      )}

      {sections.map(s => (
        <section key={s.id} id={s.id} className='mb-4'>
          <h2 className='mt-4'>
            <i className={`bi ${s.icon} me-2`} aria-hidden='true' />
            {s.title}
          </h2>
          {s.entries.map(e => (
            <Entry key={e.id} entry={e} />
          ))}
        </section>
      ))}

      <div className='card mt-4'>
        <div className='card-body'>
          <h2 className='mb-2'>{t('center.stuck')}</h2>
          <p className='mb-2'>{t('center.solveMost')}</p>
          <ul className='small mb-3'>
            <li>
              <Trans t={t} i18nKey='center.li1' components={{ 1: <Link to='/orders' /> }} />
            </li>
            <li>
              <Trans t={t} i18nKey='center.li2' components={{ 1: <Link to='/returns' />, 2: <Link to='/refunds' /> }} />
            </li>
            <li>
              <Trans t={t} i18nKey='center.li3' components={{ 1: <Link to='/login' /> }} />
            </li>
            <li>
              <Trans t={t} i18nKey='center.li4' components={{ 1: <Link to='/cookies' /> }} />
            </li>
          </ul>
          <p className='text-muted small mb-0'>
            <Trans
              t={t}
              i18nKey='center.footnote'
              values={{ email: SUPPORT_EMAIL }}
              components={{ 1: <a href={`mailto:${SUPPORT_EMAIL}`} />, 2: <Link to='/user/feedback' />, 3: <Link to='/service' /> }}
            />
          </p>
        </div>
      </div>
    </div>
  );
};

export default Help;
