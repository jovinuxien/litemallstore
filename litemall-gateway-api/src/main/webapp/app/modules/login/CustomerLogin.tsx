import React, { useState } from 'react';
import { Alert, Form, Spinner } from 'react-bootstrap';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { loginCustomerThunk } from 'app/auth/customerAuthSlice';
import GoogleSignInButton from 'app/auth/GoogleSignInButton';
import AuthShell from 'app/modules/login/AuthShell';
import { Trans, useTranslation } from 'app/i18n';

/**
 * Customer sign-in against the gateway-api edge (/auth/login, customer realm).
 * On success the JWT is persisted by the auth slice and baseAxios relays it as
 * Bearer on every /srv call. Bounces back to the route the customer was trying
 * to reach (e.g. /checkout). Wave 16: storefront-themed shell + an env-gated
 * "Sign in with Google" button (hidden until LITEMALL_GOOGLE_CLIENT_ID lands).
 */
const CustomerLogin: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { loading, errorMessage } = useAppSelector(state => state.customerAuth);
  const { t } = useTranslation('auth');

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const result = await dispatch(loginCustomerThunk({ username, password }));
    if (loginCustomerThunk.fulfilled.match(result)) {
      navigate(from, { replace: true });
    }
  };

  return (
    <AuthShell title={t('login.title')} sub={t('login.sub')}>
      {errorMessage && <Alert variant='danger'>{errorMessage}</Alert>}
      <Form onSubmit={handleSubmit}>
        <Form.Group className='mb-3'>
          <Form.Label>{t('common.username')}</Form.Label>
          <Form.Control value={username} onChange={e => setUsername(e.target.value)} required autoFocus />
        </Form.Group>
        <Form.Group className='mb-3'>
          <Form.Label>{t('common.password')}</Form.Label>
          <Form.Control type='password' value={password} onChange={e => setPassword(e.target.value)} required />
          <div className='text-end mt-1'>
            <Link to='/reset' className='small'>
              {t('login.forgot')}
            </Link>
          </div>
        </Form.Group>
        <button type='submit' className='btn btn-lm-primary w-100' disabled={loading === 'pending'}>
          {loading === 'pending' ? <Spinner animation='border' size='sm' /> : t('login.submit')}
        </button>
      </Form>
      <div className='lm-auth__divider'>{t('common.or')}</div>
      <GoogleSignInButton onSuccess={() => navigate(from, { replace: true })} />
      <div className='text-center mt-3'>
        <small className='text-muted'>
          <Trans t={t} i18nKey='login.newHere' components={{ 1: <Link to='/register' /> }} />
        </small>
      </div>
    </AuthShell>
  );
};

export default CustomerLogin;
