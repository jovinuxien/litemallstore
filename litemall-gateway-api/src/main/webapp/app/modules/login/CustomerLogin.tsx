import React, { useState } from 'react';
import { Alert, Form, Spinner } from 'react-bootstrap';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { loginCustomerThunk } from 'app/auth/customerAuthSlice';
import GoogleSignInButton from 'app/auth/GoogleSignInButton';
import AuthShell from 'app/modules/login/AuthShell';

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
    <AuthShell title='Sign in' sub='Welcome back to Trovemo.'>
      {errorMessage && <Alert variant='danger'>{errorMessage}</Alert>}
      <Form onSubmit={handleSubmit}>
        <Form.Group className='mb-3'>
          <Form.Label>Username</Form.Label>
          <Form.Control value={username} onChange={e => setUsername(e.target.value)} required autoFocus />
        </Form.Group>
        <Form.Group className='mb-3'>
          <Form.Label>Password</Form.Label>
          <Form.Control type='password' value={password} onChange={e => setPassword(e.target.value)} required />
          <div className='text-end mt-1'>
            <Link to='/reset' className='small'>
              Forgot password?
            </Link>
          </div>
        </Form.Group>
        <button type='submit' className='btn btn-lm-primary w-100' disabled={loading === 'pending'}>
          {loading === 'pending' ? <Spinner animation='border' size='sm' /> : 'Sign in'}
        </button>
      </Form>
      <div className='lm-auth__divider'>or</div>
      <GoogleSignInButton onSuccess={() => navigate(from, { replace: true })} />
      <div className='text-center mt-3'>
        <small className='text-muted'>
          New here? <Link to='/register'>Create an account</Link>
        </small>
      </div>
    </AuthShell>
  );
};

export default CustomerLogin;
