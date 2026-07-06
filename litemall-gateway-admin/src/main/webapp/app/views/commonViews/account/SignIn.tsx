import 'bootstrap/dist/css/bootstrap.min.css';
import 'app/sass/adminSass/litemall/admin-theme.scss';

import React, { lazy } from 'react';
const SignInForm = lazy(() => import('../../../components/userComponents/account/SignInForm'));

// Admin sign-in, styled to the upstream litemall-admin login: a centered card on
// the dark menu-color backdrop. The form (SignInForm) dispatches `loginAdmin`
// against the edge `/auth/login` and, on success, routes to `/admin` — auth flow
// unchanged.
const SignInView: React.FC = () => (
  <div className='lm-login'>
    <div className='lm-login-card'>
      <h4 className='lm-login-title'>litemall admin</h4>
      <div className='lm-login-sub'>Sign in to the management console</div>
      <React.Suspense fallback={<div className='text-center text-muted'>Loading…</div>}>
        <SignInForm />
      </React.Suspense>
    </div>
  </div>
);

export default SignInView;
