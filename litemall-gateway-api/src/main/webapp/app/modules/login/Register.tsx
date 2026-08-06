import React, { useState } from 'react';
import { Alert, Form, Spinner } from 'react-bootstrap';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { registerCustomerThunk } from 'app/auth/customerAuthSlice';
import GoogleSignInButton from 'app/auth/GoogleSignInButton';
import AuthShell from 'app/modules/login/AuthShell';
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

  const [username, setUsername] = useState('');
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [mobile, setMobile] = useState('');
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
      setLocalError('Passwords do not match.');
      return;
    }
    if (password.length < 8) {
      setLocalError('Password must be at least 8 characters.');
      return;
    }
    if (password === username) {
      setLocalError('Password must differ from the username.');
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
    <AuthShell title='Create your account' sub='Shop faster, track orders, and keep your history.'>
      {inviteCode && (
        <Alert variant='info' dismissible onClose={dismissInvite} className='py-2'>
          🎉 Invited by a friend
        </Alert>
      )}
      {generalError && <Alert variant='danger'>{generalError}</Alert>}
      <Form onSubmit={handleSubmit}>
        <Form.Group className='mb-3'>
          <Form.Label>Username</Form.Label>
          <Form.Control
            value={username}
            onChange={e => setUsername(e.target.value)}
            isInvalid={usernameTaken}
            required
            autoFocus
          />
          <Form.Control.Feedback type='invalid'>This username is already registered.</Form.Control.Feedback>
        </Form.Group>
        <Form.Group className='mb-3'>
          <Form.Label>
            Nickname <span className='text-muted'>(optional)</span>
          </Form.Label>
          <Form.Control value={nickname} onChange={e => setNickname(e.target.value)} />
        </Form.Group>
        <Form.Group className='mb-3'>
          <Form.Label>
            Email <span className='text-muted'>(optional)</span>
          </Form.Label>
          <Form.Control type='email' value={email} onChange={e => setEmail(e.target.value)} />
          <Form.Text className='text-muted'>Order confirmations and shipping updates go here.</Form.Text>
        </Form.Group>
        <Form.Group className='mb-3'>
          <Form.Label>
            Mobile <span className='text-muted'>(optional)</span>
          </Form.Label>
          <PhoneInput onChange={setMobile} isInvalid={mobileTaken} />
          {mobileTaken && <div className='invalid-feedback d-block'>This mobile number is already registered.</div>}
        </Form.Group>
        <Form.Group className='mb-3'>
          <Form.Label>Password</Form.Label>
          <Form.Control type='password' value={password} onChange={e => setPassword(e.target.value)} required />
          <Form.Text className='text-muted'>At least 8 characters, different from your username.</Form.Text>
        </Form.Group>
        <Form.Group className='mb-3'>
          <Form.Label>Confirm password</Form.Label>
          <Form.Control type='password' value={confirm} onChange={e => setConfirm(e.target.value)} required />
        </Form.Group>
        <button type='submit' className='btn btn-lm-primary w-100' disabled={loading === 'pending'}>
          {loading === 'pending' ? <Spinner animation='border' size='sm' /> : 'Create account'}
        </button>
      </Form>
      <div className='lm-auth__divider'>or</div>
      <GoogleSignInButton onSuccess={() => navigate(from, { replace: true })} />
      <div className='text-center mt-3'>
        <small className='text-muted'>
          Already have an account? <Link to='/login'>Sign in</Link>
        </small>
      </div>
    </AuthShell>
  );
};

export default Register;
