import React from 'react';
import { Link } from 'react-router-dom';

import LegalPlaceholder from 'app/modules/static/LegalPlaceholder';

/**
 * Privacy Policy (Wave-7 Task D). Structure + accurate processor disclosure; the
 * binding wording is a counsel deliverable — see LegalPlaceholder.
 *
 * <p>The processor table is the part engineering genuinely owns: it is a factual
 * statement about which third parties this deployment sends personal data to, and it
 * is derived from the code, not drafted. Keep it in sync — a privacy policy that
 * omits a live processor is a compliance failure regardless of how good the prose is.
 * If a wave adds an integration that touches customer data, it belongs here.
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
    name: 'Mautic (self-hosted)',
    purpose: 'Marketing email campaigns',
    data: 'Email address and nickname',
  },
];

const Privacy: React.FC = () => (
  <div className='container my-4' style={{ maxWidth: 720 }}>
    <h1 className='h4 mb-3'>Privacy Policy</h1>
    <LegalPlaceholder page='privacy policy' />

    <h2 className='h6 mt-4'>What we collect</h2>
    <p className='text-muted small'>
      Account details you give us (username, email, nickname), the addresses and orders you create, and — only
      if you accept analytics cookies — how you use the store.
    </p>

    <h2 className='h6 mt-4'>Who we share it with</h2>
    <p className='text-muted small'>
      We use the processors below. Self-hosted means the software runs on our own infrastructure and the data
      is not sent to that vendor.
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
      Analytics cookies are off until you accept them, and you can withdraw at any time from our{' '}
      <Link to='/cookies'>Cookie Policy</Link>. We also honour your browser&apos;s Do Not Track signal.
    </p>

    <h2 className='h6 mt-4'>Your rights</h2>
    <p className='text-muted small'>
      You can request access to, correction of, or deletion of your personal data. Requests are handled
      manually — <Link to='/service'>contact customer service</Link>.
      {/* Task-D note: GDPR self-service export/erasure is on the Wave-7 debt register
          (manual process behind a mailbox + SLA). The contact route above is what
          discharges the duty until that lands — do not describe it as self-service. */}
    </p>

    <h2 className='h6 mt-4'>Contact</h2>
    <p className='text-muted small'>
      Questions about this policy: <Link to='/service'>customer service</Link>.
    </p>
  </div>
);

export default Privacy;
