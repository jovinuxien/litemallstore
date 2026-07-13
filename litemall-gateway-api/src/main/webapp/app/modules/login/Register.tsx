import React, { useState } from 'react';
import { Alert, Button, Card, Container, Form, Spinner } from 'react-bootstrap';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { registerCustomerThunk } from 'app/auth/customerAuthSlice';
import { useAppDispatch, useAppSelector } from 'app/config/store';

/**
 * Customer registration against the gateway-api edge (`/auth/register`, customer
 * realm). On success the JWT is persisted by the auth slice exactly like login,
 * then we bounce to wherever the customer was headed (e.g. /checkout).
 *
 * Duplicate errors surface inline on the offending field: errno 704 = username
 * taken, errno 705 = mobile taken (docs/handoff-auth-account.md).
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
      })
    );
    if (registerCustomerThunk.fulfilled.match(result)) {
      navigate(from, { replace: true });
    }
  };

  return (
    <Container className='my-5' style={{ maxWidth: '420px' }}>
      <Card className='shadow-sm'>
        <Card.Body>
          <h3 className='mb-3'>Create your account</h3>
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
            </Form.Group>
            <Form.Group className='mb-3'>
              <Form.Label>
                Mobile <span className='text-muted'>(optional)</span>
              </Form.Label>
              <Form.Control value={mobile} onChange={e => setMobile(e.target.value)} isInvalid={mobileTaken} />
              <Form.Control.Feedback type='invalid'>This mobile number is already registered.</Form.Control.Feedback>
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
            <Button type='submit' variant='primary' className='w-100' disabled={loading === 'pending'}>
              {loading === 'pending' ? <Spinner animation='border' size='sm' /> : 'Create account'}
            </Button>
          </Form>
          <div className='text-center mt-3'>
            <small className='text-muted'>
              Already have an account? <Link to='/login'>Sign in</Link>
            </small>
          </div>
        </Card.Body>
      </Card>
    </Container>
  );
};

export default Register;
