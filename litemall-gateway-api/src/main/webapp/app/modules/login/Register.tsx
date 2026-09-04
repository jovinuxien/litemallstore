import React, { useState } from 'react';
import { Alert, Form, Spinner } from 'react-bootstrap';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { registerCustomerThunk } from 'app/auth/customerAuthSlice';
import GoogleSignInButton from 'app/auth/GoogleSignInButton';
import AuthShell from 'app/modules/login/AuthShell';
import { Trans, useTranslation } from 'app/i18n';
import PhoneInput from 'app/components/commonComponents/PhoneInput';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { fireRegisterGifts } from 'app/shared/util/couponFormat';
import { clearStashedInviteCode, getStashedInviteCode } from 'app/shared/util/invite';

/**
 * Customer registration against the gateway-api edge (`/auth/register`, customer
 * realm). On success the JWT is persisted by the auth slice exactly like login,
 * then we bounce to wherever the customer was headed (e.g. /checkout).
 *
 * Duplicate errors surface inline on the offending field: errno 704 = username
 * taken, errno 705 = mobile taken (docs/handoff-auth-account.md).
 *
 * Wave 16: storefront-themed shell, env-gated Google sign-up, and the mobile
 * field is a country dial-code selector storing one canonical E.164 number.
 */
const Register: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { loading, errorMessage, errorNumber } = useAppSelector(state => state.customerAuth);
  const { t } = useTranslation('auth');

  const [username, setUsername] = useState('');
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [mobile, setMobile] = useState('');
  const [mobileValid, setMobileValid] = useState(true);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);
  // Wave-5: invite code stashed by InviteCapture from a ?invite= landing link.
  // Dismissing the chip opts out — the code is dropped and never sent.
  const [inviteCode, setInviteCode] = useState<string | null>(() => getStashedInviteCode());

  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/';

  const usernameTaken = submitted && errorNumber === 704;
  const mobileTaken = submitted && errorNumber === 705;
  // Field-level errors render inline; everything else goes to the alert.
  const generalError = localError ?? (submitted && !usernameTaken && !mobileTaken ? errorMessage : null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLocalError(null);
    if (password !== confirm) {
      setLocalError(t('register.errors.mismatch'));
      return;
    }
    if (password.length < 8) {
      setLocalError(t('register.errors.tooShort'));
      return;
    }
    if (password === username) {
      setLocalError(t('register.errors.sameAsUsername'));
      return;
    }
    if (mobile && !mobileValid) {
      setLocalError(t('register.errors.mobileInvalid'));
      return;
    }
    setSubmitted(true);
    const result = await dispatch(
      registerCustomerThunk({
        username,
        password,
        nickname: nickname || undefined,
        email: email || undefined,
        mobile: mobile || undefined,
        inviteCode: inviteCode || undefined,
      })
    );
    if (registerCustomerThunk.fulfilled.match(result)) {
      clearStashedInviteCode();
      // Wave 18: grant register-gift coupons. Fire-and-forget by contract —
      // the session is already persisted, and a failure never blocks signup.
      fireRegisterGifts();
      navigate(from, { replace: true });
    }
  };

  const dismissInvite = () => {
    setInviteCode(null);
    clearStashedInviteCode();
  };

  return (
    <AuthShell title={t('register.title')} sub={t('register.sub')}>
      {inviteCode && (
        <Alert variant='info' dismissible onClose={dismissInvite} className='py-2'>
          {t('register.invited')}
        </Alert>
      )}
      {generalError && <Alert variant='danger'>{generalError}</Alert>}
      <Form onSubmit={handleSubmit}>
        <Form.Group className='mb-3'>
          <Form.Label>{t('common.username')}</Form.Label>
          <Form.Control
            value={username}
            onChange={e => setUsername(e.target.value)}
            isInvalid={usernameTaken}
            required
            autoFocus
          />
          <Form.Control.Feedback type='invalid'>{t('register.usernameTaken')}</Form.Control.Feedback>
        </Form.Group>
        <Form.Group className='mb-3'>
          <Form.Label>
            {t('register.nickname')} <span className='text-muted'>{t('common.optional')}</span>
          </Form.Label>
          <Form.Control value={nickname} onChange={e => setNickname(e.target.value)} />
        </Form.Group>
        <Form.Group className='mb-3'>
          <Form.Label>
            {t('register.email')} <span className='text-muted'>{t('common.optional')}</span>
          </Form.Label>
          <Form.Control type='email' value={email} onChange={e => setEmail(e.target.value)} />
          <Form.Text className='text-muted'>{t('register.emailHelp')}</Form.Text>
        </Form.Group>
        <Form.Group className='mb-3'>
          <Form.Label>
            {t('register.mobile')} <span className='text-muted'>{t('common.optional')}</span>
          </Form.Label>
          <PhoneInput onChange={setMobile} isInvalid={mobileTaken} onValidityChange={setMobileValid} />
          {mobileTaken && <div className='invalid-feedback d-block'>{t('register.mobileTaken')}</div>}
        </Form.Group>
        <Form.Group className='mb-3'>
          <Form.Label>{t('common.password')}</Form.Label>
          <Form.Control type='password' value={password} onChange={e => setPassword(e.target.value)} required />
          <Form.Text className='text-muted'>{t('register.passwordHelp')}</Form.Text>
        </Form.Group>
        <Form.Group className='mb-3'>
          <Form.Label>{t('register.confirmPassword')}</Form.Label>
          <Form.Control type='password' value={confirm} onChange={e => setConfirm(e.target.value)} required />
        </Form.Group>
        <button type='submit' className='btn btn-lm-primary w-100' disabled={loading === 'pending'}>
          {loading === 'pending' ? <Spinner animation='border' size='sm' /> : t('register.submit')}
        </button>
      </Form>
      <div className='lm-auth__divider'>{t('common.or')}</div>
      <GoogleSignInButton onSuccess={() => navigate(from, { replace: true })} />
      <div className='text-center mt-3'>
        <small className='text-muted'>
          <Trans t={t} i18nKey='register.haveAccount' components={{ 1: <Link to='/login' /> }} />
        </small>
      </div>
    </AuthShell>
  );
};

export default Register;
