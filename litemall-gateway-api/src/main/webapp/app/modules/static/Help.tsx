import React, { useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

import { FAQ_SECTIONS, FaqEntry, SUPPORT_EMAIL } from 'app/modules/static/faqData';

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
      FAQ_SECTIONS.map(s => ({
        ...s,
        entries: needle ? s.entries.filter(e => matches(e, needle)) : s.entries,
      })).filter(s => s.entries.length > 0),
    [needle]
  );

  return (
    <div className='container my-4 lm-doc' style={{ maxWidth: 720 }}>
      <h1 className='mb-3'>Help center</h1>

      <div className='input-group mb-4'>
        <span className='input-group-text'>
          <i className='bi bi-search' aria-hidden='true' />
        </span>
        <input
          type='search'
          className='form-control'
          placeholder='Search help topics, e.g. refund, coupon, delivery…'
          aria-label='Search help topics'
          value={filter}
          onChange={e => setFilter(e.target.value)}
        />
      </div>

      {sections.length === 0 && (
        <p>
          No help entries match “{filter}”. Try another word, or use the contact options below.
        </p>
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
          <h2 className='mb-2'>Still stuck?</h2>
          <p className='mb-2'>These pages solve most problems directly:</p>
          <ul className='small mb-3'>
            <li>
              <Link to='/orders'>My orders</Link> — order status, timeline, tracking, cancel, and
              after-sales requests.
            </li>
            <li>
              <Link to='/returns'>Returns &amp; Refunds</Link> — the return policy, and{' '}
              <Link to='/refunds'>your refunds</Link> to follow a request.
            </li>
            <li>
              <Link to='/login'>Sign in</Link> — includes password reset.
            </li>
            <li>
              <Link to='/cookies'>Cookie Preferences</Link> — change your analytics-cookie choice.
            </li>
          </ul>
          <p className='text-muted small mb-0'>
            Not solved? Email <a href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a> — include your
            order number if it concerns an order — or{' '}
            <Link to='/user/feedback'>send feedback</Link> from your account. Details on hours are
            on the <Link to='/service'>customer service</Link> page.
          </p>
        </div>
      </div>
    </div>
  );
};

export default Help;
