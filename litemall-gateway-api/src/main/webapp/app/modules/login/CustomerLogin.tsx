import React, { useState } from 'react';
import { Alert, Button, Card, Container, Form, Spinner } from 'react-bootstrap';
import { useLocation, useNavigate } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { loginCustomerThunk } from 'app/auth/customerAuthSlice';

/**
 * Lean customer sign-in against the gateway-api edge (/auth/login, customer
 * realm). On success the JWT is persisted by the auth slice and baseAxios
 * relays it as Bearer on every /srv call. Bounces back to the route the
 * customer was trying to reach (e.g. /checkout).
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
    <Container className='my-5' style={{ maxWidth: '420px' }}>
      <Card className='shadow-sm'>
        <Card.Body>
          <h3 className='mb-3'>Sign in</h3>
          {errorMessage && <Alert variant='danger'>{errorMessage}</Alert>}
          <Form onSubmit={handleSubmit}>
            <Form.Group className='mb-3'>
              <Form.Label>Username</Form.Label>
              <Form.Control value={username} onChange={e => setUsername(e.target.value)} required autoFocus />
            </Form.Group>
            <Form.Group className='mb-3'>
              <Form.Label>Password</Form.Label>
              <Form.Control type='password' value={password} onChange={e => setPassword(e.target.value)} required />
            </Form.Group>
            <Button type='submit' variant='primary' className='w-100' disabled={loading === 'pending'}>
              {loading === 'pending' ? <Spinner animation='border' size='sm' /> : 'Sign in'}
            </Button>
          </Form>
        </Card.Body>
      </Card>
    </Container>
  );
};

export default CustomerLogin;
