import React from 'react';
import { Link } from 'react-router-dom';

import LegalPlaceholder from 'app/modules/static/LegalPlaceholder';
import CookiePreferences from 'app/shared/tracking/CookiePreferences';

/**
 * Cookie Policy (Wave-7 Task D) and the home of the Task-C withdrawal control.
 *
 * <p>This is the destination of the footer's "Cookie Preferences" link, which
 * previously pointed at the generic contact page. The preferences control is
 * embedded rather than linked: a policy that describes a choice but makes you hunt
 * for where to exercise it is the pattern this task exists to remove.
 */
const Cookies: React.FC = () => (
  <div className='container my-4' style={{ maxWidth: 720 }}>
    <h1 className='h4 mb-3'>Cookie Policy</h1>
    <LegalPlaceholder page='cookie policy' />

    <h2 className='h6 mt-4'>Your choice</h2>
    <div className='mb-4'>
      <CookiePreferences />
    </div>

    <h2 className='h6 mt-4'>Strictly necessary</h2>
    <p className='text-muted small'>
      Set no matter what, because the store cannot work without them. These are browser storage rather than
      cookies: your sign-in token and shopping cart (cleared when you close the tab), and your analytics
      choice above (kept so we stop asking).
    </p>

    <h2 className='h6 mt-4'>Analytics — optional, off by default</h2>
    <p className='text-muted small'>
      If you accept, our self-hosted Matomo sets a first-party cookie to count visits and see which pages and
      campaigns are working. Nothing is loaded or set before you accept, and withdrawing deletes what was set.
      We never track what you type into forms. See our <Link to='/privacy'>Privacy Policy</Link>.
    </p>

    <h2 className='h6 mt-4'>Do Not Track</h2>
    <p className='text-muted small'>
      If your browser sends a Do Not Track signal we honour it and switch analytics off without asking —
      it overrides any earlier acceptance.
    </p>
  </div>
);

export default Cookies;
