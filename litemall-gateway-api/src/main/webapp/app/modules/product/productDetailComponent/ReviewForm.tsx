import React, { useState } from 'react';
import { Alert, Button, Form } from 'react-bootstrap';

import { userApi } from 'app/shared/api';

/**
 * Post-purchase review form feeding `POST /srv/comment/post` (type 0 = goods).
 * Used on the product page (Reviews CTA) and on the order-detail "Unrated"
 * flow.
 */
interface Props {
  goodsId: number | string;
  /** Called after a successful submit (e.g. refresh the review list). */
  onSubmitted?: () => void;
}

const ReviewForm: React.FC<Props> = ({ goodsId, onSubmitted }) => {
  const [star, setStar] = useState(5);
  const [content, setContent] = useState('');
  const [picUrlsRaw, setPicUrlsRaw] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [notice, setNotice] = useState<{ variant: 'info' | 'danger'; text: string } | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim()) return;
    setBusy(true);
    setNotice(null);
    const picUrls = picUrlsRaw
      .split(/[\n,]/)
      .map(s => s.trim())
      .filter(Boolean);
    try {
      await userApi.commentPost({
        type: 0,
        valueId: goodsId,
        star,
        content: content.trim(),
        hasPicture: picUrls.length > 0,
        picUrls,
      });
      setDone(true);
      onSubmitted?.();
    } catch {
      setNotice({ variant: 'danger', text: 'Your review could not be submitted. Please try again.' });
    } finally {
      setBusy(false);
    }
  };

  if (done) {
    return (
      <Alert variant='success' className='mb-0'>
        Thanks! Your review has been submitted.
      </Alert>
    );
  }

  return (
    <Form onSubmit={submit} className='lm-review-form'>
      <div className='mb-2' role='radiogroup' aria-label='Rating'>
        {[1, 2, 3, 4, 5].map(i => (
          <button
            key={i}
            type='button'
            className='btn btn-link p-0 me-1 fs-5 text-warning text-decoration-none'
            aria-label={`${i} star${i > 1 ? 's' : ''}`}
            onClick={() => setStar(i)}
          >
            <i className={`bi ${i <= star ? 'bi-star-fill' : 'bi-star'}`} />
          </button>
        ))}
      </div>
      <Form.Control
        as='textarea'
        rows={3}
        maxLength={1023}
        placeholder='Share your experience with this product…'
        value={content}
        onChange={e => setContent(e.target.value)}
        className='mb-2'
        required
      />
      <Form.Control
        size='sm'
        placeholder='Image URLs (optional, comma-separated)'
        value={picUrlsRaw}
        onChange={e => setPicUrlsRaw(e.target.value)}
        className='mb-2'
      />
      {notice && (
        <Alert variant={notice.variant} className='py-2'>
          {notice.text}
        </Alert>
      )}
      <Button type='submit' size='sm' variant='primary' disabled={busy || !content.trim()}>
        {busy ? 'Submitting…' : 'Submit review'}
      </Button>
    </Form>
  );
};

export default ReviewForm;
