import React from 'react';
import { Link } from 'react-router-dom';

import CookiePreferences from 'app/shared/tracking/CookiePreferences';

/**
 * Cookie Policy (binding copy, Wave 15) and the home of the consent withdrawal
 * control. The preferences control is embedded rather than linked: a policy
 * that describes a choice but makes you hunt for where to exercise it is the
 * pattern this page exists to remove.
 */
const Cookies: React.FC = () => (
  <div className='container my-4' style={{ maxWidth: 720 }}>
    <h1 className='h4 mb-3'>Cookie Policy</h1>
    <p className='text-muted small'>Last updated: 31 July 2026</p>

    <h2 className='h6 mt-4'>Your choice</h2>
    <div className='mb-4'>
      <CookiePreferences />
    </div>

    <h2 className='h6 mt-4'>Strictly necessary</h2>
    <p className='text-muted small'>
      Set no matter what, because the store cannot work without them. These are browser storage rather than
      cookies: your sign-in token and shopping cart (cleared when you close the tab), and your consent choice
      above (kept so we stop asking).
    </p>

    <h2 className='h6 mt-4'>Analytics — optional, off by default</h2>
    <p className='text-muted small'>
      If you accept, our self-hosted Matomo sets a first-party cookie to count visits and see which pages and
      campaigns are working. Nothing is loaded or set before you accept, and withdrawing deletes what was set.
      We never track what you type into forms. See our <Link to='/privacy'>Privacy Policy</Link>.
    </p>

    <h2 className='h6 mt-4'>Marketing — optional, off by default</h2>
    <p className='text-muted small'>
      If you accept, the Meta (Facebook) Pixel measures how our advertising performs: it reports page views
      and shopping events (product views, add-to-cart, purchases) to Meta, which sets its own cookie. It is
      covered by the same single choice above — nothing from Meta is loaded before you accept, and withdrawing
      stops all further reporting. Meta&apos;s own processing is described in{' '}
      <a href='https://www.facebook.com/privacy/policy/' target='_blank' rel='noreferrer'>
        Meta&apos;s privacy policy
      </a>
      .
    </p>

    <h2 className='h6 mt-4'>Do Not Track</h2>
    <p className='text-muted small'>
      If your browser sends a Do Not Track signal we honour it and switch analytics and marketing off without
      asking — it overrides any earlier acceptance.
    </p>
  </div>
);

export default Cookies;
