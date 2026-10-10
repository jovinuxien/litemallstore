import React, { useState } from 'react';
import { Alert, Button, Form } from 'react-bootstrap';

import ImageUploader from 'app/components/commonComponents/ImageUploader';
import { userApi } from 'app/shared/api';
import { useTranslation } from 'app/i18n';
import { describeError } from 'app/i18n/errors';

/** Reviews are purchase-gated: only a customer who received the product may review it. */
export const ERRNO_NOT_PURCHASED = 670;
/** …and each purchase can be reviewed once. */
export const ERRNO_ALREADY_REVIEWED = 671;

/** How a review attempt ended for good — nothing left for the customer to retry. */
export type ReviewOutcome = 'submitted' | 'already-reviewed' | 'not-eligible';

/**
 * Post-purchase review form feeding `POST /srv/comment/post` (type 0 = goods).
 * Used on the product page (Reviews CTA) and on the order-detail "Unrated"
 * flow.
 *
 * The endpoint is purchase-gated (goods-management F17) and answers two typed
 * refusals: 670 "Only customers who received this product can review it" and 671
 * "You have already reviewed this purchase". Both are FINAL — retrying cannot change
 * them — so the form closes on the server's own words instead of the generic "could
 * not be submitted, try again" it used to show for every error. Anything else stays
 * a retryable failure, in the server's words when it gave any.
 */
interface Props {
  goodsId: number | string;
  /**
   * The order this review is written from. Sent as `orderId`, which PINS the purchase
   * check to that order (it never falls back to another purchase of the same product).
   * Omitted on the product page, where the server picks the oldest unreviewed purchase.
   */
  orderId?: number | string;
  /** Called after a successful submit (e.g. refresh the review list). */
  onSubmitted?: () => void;
  /** Called once when the attempt is over for good, with the text the form now shows. */
  onSettled?: (outcome: ReviewOutcome, message: string) => void;
}

/** The typed errno and customer text of a failed post, whichever way it arrived. */
const refusalOf = (error: unknown): { errno?: number; text?: string } => {
  const thrown = error as { errno?: unknown; message?: string; response?: { data?: { errno?: unknown; errmsg?: string } } };
  // `unwrap` rethrows an errno envelope as ApiError: errno + already-localised text.
  if (typeof thrown?.errno === 'number') return { errno: thrown.errno, text: thrown.message };
  // The same envelope on a non-2xx response.
  const data = thrown?.response?.data;
  if (typeof data?.errno === 'number') return { errno: data.errno, text: describeError(data.errno, data.errmsg) };
  return {};
};

const ReviewForm: React.FC<Props> = ({ goodsId, orderId, onSubmitted, onSettled }) => {
  const { t } = useTranslation('product');
  const [star, setStar] = useState(5);
  const [content, setContent] = useState('');
  const [picUrls, setPicUrls] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  // A final refusal (670 / 671): the form is replaced by the server's words.
  const [refused, setRefused] = useState<{ variant: 'info' | 'warning'; text: string } | null>(null);
  const [notice, setNotice] = useState<{ variant: 'info' | 'danger'; text: string } | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim()) return;
    setBusy(true);
    setNotice(null);
    try {
      await userApi.commentPost({
        type: 0,
        valueId: goodsId,
        star,
        content: content.trim(),
        hasPicture: picUrls.length > 0,
        picUrls,
        ...(orderId != null ? { orderId } : {}),
      });
      setDone(true);
      onSubmitted?.();
      onSettled?.('submitted', t('reviewForm.thanks'));
    } catch (error) {
      const { errno, text } = refusalOf(error);
      if (errno === ERRNO_ALREADY_REVIEWED || errno === ERRNO_NOT_PURCHASED) {
        const message = text || t('reviewForm.failed');
        const already = errno === ERRNO_ALREADY_REVIEWED;
        setRefused({ variant: already ? 'info' : 'warning', text: message });
        onSettled?.(already ? 'already-reviewed' : 'not-eligible', message);
      } else {
        // Retryable. The server's words when it gave any; a dropped connection gets ours.
        setNotice({ variant: 'danger', text: text || t('reviewForm.failed') });
      }
    } finally {
      setBusy(false);
    }
  };

  if (done) {
    return (
      <Alert variant='success' className='mb-0'>
        {t('reviewForm.thanks')}
      </Alert>
    );
  }

  if (refused) {
    return (
      <Alert variant={refused.variant} className='mb-0'>
        {refused.text}
      </Alert>
    );
  }

  return (
    <Form onSubmit={submit} className='lm-review-form'>
      <div className='mb-2' role='radiogroup' aria-label={t('reviewForm.ratingAria')}>
        {[1, 2, 3, 4, 5].map(i => (
          <button
            key={i}
            type='button'
            className='btn btn-link p-0 me-1 fs-5 text-warning text-decoration-none'
            aria-label={t('reviewForm.star', { count: i })}
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
        placeholder={t('reviewForm.placeholder')}
        value={content}
        onChange={e => setContent(e.target.value)}
        className='mb-2'
        required
      />
      <div className='mb-2'>
        <ImageUploader value={picUrls} onChange={setPicUrls} max={5} disabled={busy} />
      </div>
      {notice && (
        <Alert variant={notice.variant} className='py-2'>
          {notice.text}
        </Alert>
      )}
      <Button type='submit' size='sm' variant='primary' disabled={busy || !content.trim()}>
        {busy ? t('reviewForm.submitting') : t('reviewForm.submit')}
      </Button>
    </Form>
  );
};

export default ReviewForm;
