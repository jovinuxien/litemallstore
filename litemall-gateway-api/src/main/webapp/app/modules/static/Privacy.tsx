import React from 'react';
import { Link } from 'react-router-dom';

import { SELLER } from 'app/modules/static/seller';

/**
 * Privacy Policy (binding copy, Wave 15; structure from Wave-7 Task D).
 *
 * <p>The processor table is the part engineering genuinely owns: it is a factual
 * statement about which third parties this deployment sends personal data to, and it
 * is derived from the code, not drafted. Keep it in sync — a privacy policy that
 * omits a live processor is a compliance failure regardless of how good the prose is.
 * If a wave adds an integration that touches customer data, it belongs here.
 * (Wave 15 added the Meta Pixel row when the consent-gated pixel shipped.)
 */

const PROCESSORS = [
  {
    name: 'Stripe',
    purpose: 'Card payment processing and tax calculation',
    data: 'Payment card details (entered directly with Stripe — they never reach our servers), billing address, order amount',
  },
  {
    name: 'CJ Dropshipping',
    purpose: 'Order fulfilment and shipping for dropshipped items',
    data: 'Recipient name, full shipping address, contact details and the items ordered',
  },
  {
    name: 'Matomo (self-hosted)',
    purpose: 'Website analytics — only with your consent',
    data: 'Pages viewed, referring campaign (utm_*), and a first-party cookie identifier',
  },
  {
    name: 'Meta (Facebook) Pixel',
    purpose: 'Advertising measurement — only with your consent',
    data: 'Pages viewed and shopping events (product views, cart, purchases) tied to a Meta cookie identifier',
  },
  {
    name: 'Mautic (self-hosted)',
    purpose: 'Marketing email campaigns',
    data: 'Email address and nickname',
  },
];

const Privacy: React.FC = () => (
  <div className='container my-4' style={{ maxWidth: 720 }}>
    <h1 className='h4 mb-3'>Privacy Policy</h1>
    <p className='text-muted small'>Last updated: 31 July 2026</p>

    <h2 className='h6 mt-4'>Who is responsible</h2>
    <p className='text-muted small'>
      The data controller for trovemo.com is <strong>{SELLER.name}</strong>, {SELLER.address} —{' '}
      <a href={`mailto:${SELLER.email}`}>{SELLER.email}</a>.
    </p>

    <h2 className='h6 mt-4'>What we collect and why</h2>
    <p className='text-muted small'>
      Account details you give us (username, email, nickname), the addresses and orders you create, and — only
      if you accept analytics and marketing cookies — how you use the store. We process order data to perform
      our contract with you, keep accounting records because Swedish bookkeeping law requires it (seven
      years), and use analytics and advertising measurement only on your consent, which you can withdraw at
      any time.
    </p>

    <h2 className='h6 mt-4'>Who we share it with</h2>
    <p className='text-muted small'>
      We use the processors below and share nothing beyond what each needs. Self-hosted means the software
      runs on our own infrastructure and the data is not sent to that vendor.
    </p>
    <div className='table-responsive'>
      <table className='table table-sm align-middle'>
        <thead>
          <tr>
            <th scope='col'>Processor</th>
            <th scope='col'>Purpose</th>
            <th scope='col'>Data involved</th>
          </tr>
        </thead>
        <tbody>
          {PROCESSORS.map(p => (
            <tr key={p.name}>
              <td className='fw-semibold'>{p.name}</td>
              <td className='small text-muted'>{p.purpose}</td>
              <td className='small text-muted'>{p.data}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>

    <h2 className='h6 mt-4'>Cookies and analytics</h2>
    <p className='text-muted small'>
      Analytics and marketing cookies are off until you accept them, and you can withdraw at any time from our{' '}
      <Link to='/cookies'>Cookie Policy</Link>. We also honour your browser&apos;s Do Not Track signal.
    </p>

    <h2 className='h6 mt-4'>Your rights</h2>
    <p className='text-muted small'>
      You can request access to, correction of, deletion of, or a copy (portability) of your personal data,
      and you can object to or restrict processing. Requests are handled manually —{' '}
      <a href={`mailto:${SELLER.email}`}>email us</a> or <Link to='/service'>contact customer service</Link>{' '}
      and we will respond within one month. You also have the right to complain to the Swedish data protection
      authority, IMY (imy.se), or to your local supervisory authority.
      {/* GDPR SELF-SERVICE export/erasure remains on the debt register (Wave-7) —
          the mailbox + one-month SLA above is what discharges the duty until that
          lands. Do not describe it as self-service. */}
    </p>

    <h2 className='h6 mt-4'>Contact</h2>
    <p className='text-muted small'>
      Questions about this policy: <a href={`mailto:${SELLER.email}`}>{SELLER.email}</a> ·{' '}
      <Link to='/service'>customer service</Link>.
    </p>
  </div>
);

export default Privacy;
