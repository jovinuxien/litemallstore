import React, { useEffect, useState } from 'react';
import { Alert, Button, Form, Nav, Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import AuthShell from 'app/modules/login/AuthShell';

import { useAppSelector } from 'app/config/store';
import { authApi } from 'app/shared/api';

/**
 * Password self-service (Wave 4 Task A). Two tabs:
 *
 * - "Change password" (always shown): authenticated `POST /auth/reset`
 *   {oldPassword, newPassword} — wrong old password → errno 700 inline.
 * - "Forgot password" (hidden when the edge answers errno 701 = the email
 *   flow is disabled): `POST /auth/reset/request` {email} then
 *   `POST /auth/reset/confirm` {token, newPassword} — bad token → 703 inline.
 *
 * The forgot-tab probe posts an empty email: a disabled flow answers 701
 * regardless of body, an enabled one 402 — no account information leaks.
 */
const ResetPassword: React.FC = () => {
  const isAuthenticated = useAppSelector(state => state.customerAuth.data.isAuthenticated);

  const [tab, setTab] = useState<'change' | 'forgot'>('change');
  const [forgotAvailable, setForgotAvailable] = useState(false);

  // Change tab state.
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [changing, setChanging] = useState(false);
  const [changed, setChanged] = useState(false);
  const [wrongOld, setWrongOld] = useState(false);
  const [changeError, setChangeError] = useState<string | null>(null);

  // Forgot tab state.
  const [email, setEmail] = useState('');
  const [requesting, setRequesting] = useState(false);
  const [requested, setRequested] = useState(false);
  const [token, setToken] = useState('');
  const [forgotPassword, setForgotPassword] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [badToken, setBadToken] = useState(false);
  const [forgotError, setForgotError] = useState<string | null>(null);

  useEffect(() => {
    // Capability probe — errno 701 means the email flow is disabled and the
    // tab stays hidden (per the handoff contract).
    authApi
      .resetRequest({ email: '' })
      .then(env => setForgotAvailable(env.errno !== 701))
      .catch(() => setForgotAvailable(false));
  }, []);

  const submitChange = async (e: React.FormEvent) => {
    e.preventDefault();
    setChangeError(null);
    setWrongOld(false);
    setChanged(false);
    if (newPassword !== confirm) {
      setChangeError('New passwords do not match.');
      return;
    }
    setChanging(true);
    try {
      const env = await authApi.reset({ oldPassword, newPassword });
      if (env.errno === 0) {
        setChanged(true);
        setOldPassword('');
        setNewPassword('');
        setConfirm('');
      } else if (env.errno === 700) {
        setWrongOld(true);
      } else {
        setChangeError(env.errmsg || 'Password change failed.');
      }
    } catch {
      setChangeError('Password change failed.');
    } finally {
      setChanging(false);
    }
  };

  const submitRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    setForgotError(null);
    setRequesting(true);
    try {
      const env = await authApi.resetRequest({ email });
      if (env.errno === 0) {
        setRequested(true);
      } else {
        setForgotError(env.errmsg || 'Request failed.');
      }
    } catch {
      setForgotError('Request failed.');
    } finally {
      setRequesting(false);
    }
  };

  const submitConfirm = async (e: React.FormEvent) => {
    e.preventDefault();
    setForgotError(null);
    setBadToken(false);
    setConfirming(true);
    try {
      const env = await authApi.resetConfirm({ token, newPassword: forgotPassword });
      if (env.errno === 0) {
        setConfirmed(true);
      } else if (env.errno === 703) {
        setBadToken(true);
      } else {
        setForgotError(env.errmsg || 'Reset failed.');
      }
    } catch {
      setForgotError('Reset failed.');
    } finally {
      setConfirming(false);
    }
  };

  return (
    <AuthShell title='Password' sub='Change your password, or recover a forgotten one.'>
          {forgotAvailable && (
            <Nav variant='tabs' activeKey={tab} onSelect={k => setTab((k as 'change' | 'forgot') ?? 'change')} className='mb-3'>
              <Nav.Item>
                <Nav.Link eventKey='change'>Change password</Nav.Link>
              </Nav.Item>
              <Nav.Item>
                <Nav.Link eventKey='forgot'>Forgot password</Nav.Link>
              </Nav.Item>
            </Nav>
          )}

          {tab === 'change' && (
            <>
              {!isAuthenticated && (
                <Alert variant='warning'>
                  You need to <Link to='/login' state={{ from: { pathname: '/reset' } }}>sign in</Link> to change your
                  password{forgotAvailable ? ', or use the “Forgot password” tab' : ''}.
                </Alert>
              )}
              {changed && <Alert variant='success'>Password changed. Other signed-in sessions were logged out.</Alert>}
              {changeError && <Alert variant='danger'>{changeError}</Alert>}
              <Form onSubmit={submitChange}>
                <Form.Group className='mb-3'>
                  <Form.Label>Current password</Form.Label>
                  <Form.Control
                    type='password'
                    value={oldPassword}
                    onChange={e => setOldPassword(e.target.value)}
                    isInvalid={wrongOld}
                    required
                    autoFocus
                  />
                  <Form.Control.Feedback type='invalid'>The current password is incorrect.</Form.Control.Feedback>
                </Form.Group>
                <Form.Group className='mb-3'>
                  <Form.Label>New password</Form.Label>
                  <Form.Control type='password' value={newPassword} onChange={e => setNewPassword(e.target.value)} required />
                  <Form.Text className='text-muted'>At least 8 characters, different from your username.</Form.Text>
                </Form.Group>
                <Form.Group className='mb-3'>
                  <Form.Label>Confirm new password</Form.Label>
                  <Form.Control type='password' value={confirm} onChange={e => setConfirm(e.target.value)} required />
                </Form.Group>
                <Button type='submit' variant='primary' className='w-100' disabled={changing || !isAuthenticated}>
                  {changing ? <Spinner animation='border' size='sm' /> : 'Change password'}
                </Button>
              </Form>
            </>
          )}

          {tab === 'forgot' && forgotAvailable && (
            <>
              {confirmed ? (
                <Alert variant='success'>
                  Password reset. <Link to='/login'>Sign in</Link> with your new password.
                </Alert>
              ) : (
                <>
                  {forgotError && <Alert variant='danger'>{forgotError}</Alert>}
                  {!requested ? (
                    <Form onSubmit={submitRequest}>
                      <Form.Group className='mb-3'>
                        <Form.Label>Account email</Form.Label>
                        <Form.Control type='email' value={email} onChange={e => setEmail(e.target.value)} required />
                        <Form.Text className='text-muted'>We’ll send a reset code if this email is registered.</Form.Text>
                      </Form.Group>
                      <Button type='submit' variant='primary' className='w-100' disabled={requesting}>
                        {requesting ? <Spinner animation='border' size='sm' /> : 'Send reset code'}
                      </Button>
                    </Form>
                  ) : (
                    <>
                      <Alert variant='info'>If that email is registered, a reset code is on its way (valid 30 minutes).</Alert>
                      <Form onSubmit={submitConfirm}>
                        <Form.Group className='mb-3'>
                          <Form.Label>Reset code</Form.Label>
                          <Form.Control value={token} onChange={e => setToken(e.target.value)} isInvalid={badToken} required />
                          <Form.Control.Feedback type='invalid'>This code is invalid or has expired.</Form.Control.Feedback>
                        </Form.Group>
                        <Form.Group className='mb-3'>
                          <Form.Label>New password</Form.Label>
                          <Form.Control
                            type='password'
                            value={forgotPassword}
                            onChange={e => setForgotPassword(e.target.value)}
                            required
                          />
                        </Form.Group>
                        <Button type='submit' variant='primary' className='w-100' disabled={confirming}>
                          {confirming ? <Spinner animation='border' size='sm' /> : 'Reset password'}
                        </Button>
                      </Form>
                    </>
                  )}
                </>
              )}
            </>
          )}
    </AuthShell>
  );
};

export default ResetPassword;
