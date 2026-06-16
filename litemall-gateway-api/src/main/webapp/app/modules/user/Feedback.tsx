import React, { useState } from 'react';
import { Alert, Button, Card, Container, Form, Spinner } from 'react-bootstrap';

import { isMissingEndpoint, userApi } from 'app/shared/api';
import './user.scss';

/**
 * Feedback form, modelled on litemall-vue `user/module-feedback`. Posts to
 * `/srv/feedback/submit`. Graceful when not live (follow-up: user worktree).
 */
const Feedback: React.FC = () => {
  const [content, setContent] = useState('');
  const [mobile, setMobile] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await userApi.feedbackSubmit({ content, mobile, type: 'feedback' });
      setDone(true);
      setContent('');
      setMobile('');
    } catch (err) {
      if (isMissingEndpoint(err)) setError('Feedback submission isn’t available yet.');
      else setError((err as { message?: string })?.message ?? 'Could not send feedback.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Container className='my-4' style={{ maxWidth: 560 }}>
      <h1 className='h4 mb-3'>Send feedback</h1>
      <Card>
        <Card.Body>
          {done && <Alert variant='success'>Thanks — your feedback has been sent.</Alert>}
          {error && <Alert variant='warning'>{error}</Alert>}
          <Form onSubmit={submit}>
            <Form.Group className='mb-3'>
              <Form.Label>Your message</Form.Label>
              <Form.Control as='textarea' rows={5} value={content} onChange={e => setContent(e.target.value)} required />
            </Form.Group>
            <Form.Group className='mb-3'>
              <Form.Label>Contact (optional)</Form.Label>
              <Form.Control value={mobile} onChange={e => setMobile(e.target.value)} placeholder='Phone or email' />
            </Form.Group>
            <Button type='submit' variant='primary' disabled={busy || !content.trim()}>
              {busy ? <Spinner animation='border' size='sm' /> : 'Send feedback'}
            </Button>
          </Form>
        </Card.Body>
      </Card>
    </Container>
  );
};

export default Feedback;
