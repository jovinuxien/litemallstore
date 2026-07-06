import React, { useState } from 'react';
import { Alert, Button, Card, Container, Form, Spinner } from 'react-bootstrap';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { registerCustomerThunk } from 'app/auth/customerAuthSlice';
import { useAppDispatch, useAppSelector } from 'app/config/store';

/**
 * Customer registration against the gateway-api edge (`/auth/register`, customer
 * realm). On success the JWT is persisted by the auth slice exactly like login,
 * then we bounce to wherever the customer was headed (e.g. /checkout).
 * Mirrors litemall-vue's register view.
 */
const Register: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { loading, errorMessage } = useAppSelector(state => state.customerAuth);

  const [username, setUsername] = useState('');
  const [mobile, setMobile] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);

  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLocalError(null);
    if (password !== confirm) {
      setLocalError('Passwords do not match.');
      return;
    }
    const result = await dispatch(registerCustomerThunk({ username, password, mobile }));
    if (registerCustomerThunk.fulfilled.match(result)) {
      navigate(from, { replace: true });
    }
  };

  return (
    <Container className='my-5' style={{ maxWidth: '420px' }}>
      <Card className='shadow-sm'>
        <Card.Body>
          <h3 className='mb-3'>Create your account</h3>
          {(localError || errorMessage) && <Alert variant='danger'>{localError ?? errorMessage}</Alert>}
          <Form onSubmit={handleSubmit}>
            <Form.Group className='mb-3'>
              <Form.Label>Username</Form.Label>
              <Form.Control value={username} onChange={e => setUsername(e.target.value)} required autoFocus />
            </Form.Group>
            <Form.Group className='mb-3'>
              <Form.Label>Mobile</Form.Label>
              <Form.Control value={mobile} onChange={e => setMobile(e.target.value)} />
            </Form.Group>
            <Form.Group className='mb-3'>
              <Form.Label>Password</Form.Label>
              <Form.Control type='password' value={password} onChange={e => setPassword(e.target.value)} required />
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
