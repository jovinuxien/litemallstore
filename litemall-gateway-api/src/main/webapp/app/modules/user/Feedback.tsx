import React, { useState } from 'react';
import { Alert, Form, Spinner } from 'react-bootstrap';

import { userApi } from 'app/shared/api';
import { CellGroup, Page, PageHead } from 'app/components/commonComponents/storefront';
import './user.scss';

const FEEDBACK_TYPES = [
  { value: 'feedback', label: 'Suggestion' },
  { value: 'complaint', label: 'Complaint' },
  { value: 'bug', label: 'Bug' },
  { value: 'other', label: 'Other' },
];

const CONTENT_MAX = 500;

/**
 * Feedback form, modelled on litemall-vue `user/module-feedback`. Posts to
 * `/srv/feedback/submit`.
 */
const Feedback: React.FC = () => {
  const [type, setType] = useState('feedback');
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
      await userApi.feedbackSubmit({ content, mobile, type });
      setDone(true);
      setContent('');
      setMobile('');
    } catch (err) {
      setError((err as { message?: string })?.message ?? 'Could not send feedback.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Page>
      <PageHead title='Feedback' sub='Tell us what you think — suggestions, complaints, or bugs.' />
      <div className='container' style={{ maxWidth: 560 }}>
        <CellGroup title='Feedback'>
          <Form onSubmit={submit} className='p-3'>
            {done && <Alert variant='success'>Thanks — your feedback has been sent.</Alert>}
            {error && <Alert variant='warning'>{error}</Alert>}

            <Form.Group className='mb-3'>
              <Form.Label className='small text-muted mb-1'>Type</Form.Label>
              <Form.Select value={type} onChange={e => setType(e.target.value)}>
                {FEEDBACK_TYPES.map(t => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </Form.Select>
            </Form.Group>

            <Form.Group className='mb-3'>
              <Form.Label className='small text-muted mb-1'>Your message *</Form.Label>
              <Form.Control
                as='textarea'
                rows={5}
                maxLength={CONTENT_MAX}
                placeholder='Share the details of your feedback'
                value={content}
                onChange={e => setContent(e.target.value)}
                required
              />
              <div className='text-end small text-muted'>
                {content.length}/{CONTENT_MAX}
              </div>
            </Form.Group>

            <Form.Group className='mb-3'>
              <Form.Label className='small text-muted mb-1'>Contact (optional)</Form.Label>
              <Form.Control value={mobile} onChange={e => setMobile(e.target.value)} placeholder='Phone or email' />
            </Form.Group>

            <button type='submit' className='btn btn-lm-primary' disabled={busy || !content.trim()}>
              {busy ? <Spinner animation='border' size='sm' /> : 'Submit feedback'}
            </button>
          </Form>
        </CellGroup>
      </div>
    </Page>
  );
};

export default Feedback;
