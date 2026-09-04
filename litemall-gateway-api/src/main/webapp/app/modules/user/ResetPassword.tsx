import React, { useEffect, useState } from 'react';
import { Alert, Button, Form, Nav, Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import AuthShell from 'app/modules/login/AuthShell';
import { Trans, useTranslation } from 'app/i18n';

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
  const { t } = useTranslation('auth');

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
      setChangeError(t('reset.mismatch'));
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
        setChangeError(env.errmsg || t('reset.changeFailed'));
      }
    } catch {
      setChangeError(t('reset.changeFailed'));
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
        setForgotError(env.errmsg || t('reset.requestFailed'));
      }
    } catch {
      setForgotError(t('reset.requestFailed'));
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
        setForgotError(env.errmsg || t('reset.resetFailed'));
      }
    } catch {
      setForgotError(t('reset.resetFailed'));
    } finally {
      setConfirming(false);
    }
  };

  return (
    <AuthShell title={t('reset.title')} sub={t('reset.sub')}>
          {forgotAvailable && (
            <Nav variant='tabs' activeKey={tab} onSelect={k => setTab((k as 'change' | 'forgot') ?? 'change')} className='mb-3'>
              <Nav.Item>
                <Nav.Link eventKey='change'>{t('reset.tabChange')}</Nav.Link>
              </Nav.Item>
              <Nav.Item>
                <Nav.Link eventKey='forgot'>{t('reset.tabForgot')}</Nav.Link>
              </Nav.Item>
            </Nav>
          )}

          {tab === 'change' && (
            <>
              {!isAuthenticated && (
                <Alert variant='warning'>
                  <Trans
                    t={t}
                    i18nKey={forgotAvailable ? 'reset.needSignInOrForgot' : 'reset.needSignIn'}
                    components={{ 1: <Link to='/login' state={{ from: { pathname: '/reset' } }} /> }}
                  />
                </Alert>
              )}
              {changed && <Alert variant='success'>{t('reset.changed')}</Alert>}
              {changeError && <Alert variant='danger'>{changeError}</Alert>}
              <Form onSubmit={submitChange}>
                <Form.Group className='mb-3'>
                  <Form.Label>{t('reset.currentPassword')}</Form.Label>
                  <Form.Control
                    type='password'
                    value={oldPassword}
                    onChange={e => setOldPassword(e.target.value)}
                    isInvalid={wrongOld}
                    required
                    autoFocus
                  />
                  <Form.Control.Feedback type='invalid'>{t('reset.wrongCurrent')}</Form.Control.Feedback>
                </Form.Group>
                <Form.Group className='mb-3'>
                  <Form.Label>{t('reset.newPassword')}</Form.Label>
                  <Form.Control type='password' value={newPassword} onChange={e => setNewPassword(e.target.value)} required />
                  <Form.Text className='text-muted'>{t('reset.passwordHelp')}</Form.Text>
                </Form.Group>
                <Form.Group className='mb-3'>
                  <Form.Label>{t('reset.confirmNew')}</Form.Label>
                  <Form.Control type='password' value={confirm} onChange={e => setConfirm(e.target.value)} required />
                </Form.Group>
                <Button type='submit' variant='primary' className='w-100' disabled={changing || !isAuthenticated}>
                  {changing ? <Spinner animation='border' size='sm' /> : t('reset.changeSubmit')}
                </Button>
              </Form>
            </>
          )}

          {tab === 'forgot' && forgotAvailable && (
            <>
              {confirmed ? (
                <Alert variant='success'>
                  <Trans t={t} i18nKey='reset.resetDone' components={{ 1: <Link to='/login' /> }} />
                </Alert>
              ) : (
                <>
                  {forgotError && <Alert variant='danger'>{forgotError}</Alert>}
                  {!requested ? (
                    <Form onSubmit={submitRequest}>
                      <Form.Group className='mb-3'>
                        <Form.Label>{t('reset.accountEmail')}</Form.Label>
                        <Form.Control type='email' value={email} onChange={e => setEmail(e.target.value)} required />
                        <Form.Text className='text-muted'>{t('reset.emailHelp')}</Form.Text>
                      </Form.Group>
                      <Button type='submit' variant='primary' className='w-100' disabled={requesting}>
                        {requesting ? <Spinner animation='border' size='sm' /> : t('reset.sendCode')}
                      </Button>
                    </Form>
                  ) : (
                    <>
                      <Alert variant='info'>{t('reset.codeSent')}</Alert>
                      <Form onSubmit={submitConfirm}>
                        <Form.Group className='mb-3'>
                          <Form.Label>{t('reset.code')}</Form.Label>
                          <Form.Control value={token} onChange={e => setToken(e.target.value)} isInvalid={badToken} required />
                          <Form.Control.Feedback type='invalid'>{t('reset.badCode')}</Form.Control.Feedback>
                        </Form.Group>
                        <Form.Group className='mb-3'>
                          <Form.Label>{t('reset.newPassword')}</Form.Label>
                          <Form.Control
                            type='password'
                            value={forgotPassword}
                            onChange={e => setForgotPassword(e.target.value)}
                            required
                          />
                        </Form.Group>
                        <Button type='submit' variant='primary' className='w-100' disabled={confirming}>
                          {confirming ? <Spinner animation='border' size='sm' /> : t('reset.resetSubmit')}
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
