import React, { useState } from 'react';
import { Alert, Form, Spinner } from 'react-bootstrap';

import { userApi } from 'app/shared/api';
import { useTranslation } from 'app/i18n';
import ImageUploader from 'app/components/commonComponents/ImageUploader';
import { CellGroup, Page, PageHead } from 'app/components/commonComponents/storefront';
import './user.scss';

const FEEDBACK_TYPES = ['feedback', 'complaint', 'bug', 'other'] as const;

const CONTENT_MAX = 500;

/**
 * Feedback form, modelled on litemall-vue `user/module-feedback`. Posts to
 * `/srv/feedback/submit`.
 */
const Feedback: React.FC = () => {
  const { t } = useTranslation('user');
  const [type, setType] = useState('feedback');
  const [content, setContent] = useState('');
  const [mobile, setMobile] = useState('');
  const [picUrls, setPicUrls] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      // picUrls rides the legacy LitemallFeedback pic_urls column
      // (goods-management engagement contract).
      await userApi.feedbackSubmit({ content, mobile, type, picUrls });
      setDone(true);
      setContent('');
      setMobile('');
      setPicUrls([]);
    } catch (err) {
      setError((err as { message?: string })?.message ?? t('feedback.sendFailed'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Page>
      <PageHead title={t('feedback.title')} sub={t('feedback.sub')} />
      <div className='container' style={{ maxWidth: 560 }}>
        <CellGroup title={t('feedback.title')}>
          <Form onSubmit={submit} className='p-3'>
            {done && <Alert variant='success'>{t('feedback.sent')}</Alert>}
            {error && <Alert variant='warning'>{error}</Alert>}

            <Form.Group className='mb-3'>
              <Form.Label className='small text-muted mb-1'>{t('feedback.type')}</Form.Label>
              <Form.Select value={type} onChange={e => setType(e.target.value)}>
                {FEEDBACK_TYPES.map(v => (
                  <option key={v} value={v}>
                    {t(`feedback.types.${v}`)}
                  </option>
                ))}
              </Form.Select>
            </Form.Group>

            <Form.Group className='mb-3'>
              <Form.Label className='small text-muted mb-1'>{t('feedback.message')}</Form.Label>
              <Form.Control
                as='textarea'
                rows={5}
                maxLength={CONTENT_MAX}
                placeholder={t('feedback.messagePlaceholder')}
                value={content}
                onChange={e => setContent(e.target.value)}
                required
              />
              <div className='text-end small text-muted'>
                {content.length}/{CONTENT_MAX}
              </div>
            </Form.Group>

            <Form.Group className='mb-3'>
              <Form.Label className='small text-muted mb-1'>{t('feedback.contact')}</Form.Label>
              <Form.Control value={mobile} onChange={e => setMobile(e.target.value)} placeholder={t('feedback.contactPlaceholder')} />
            </Form.Group>

            <Form.Group className='mb-3'>
              <Form.Label className='small text-muted mb-1'>{t('feedback.screenshots')}</Form.Label>
              <ImageUploader value={picUrls} onChange={setPicUrls} max={3} disabled={busy} />
            </Form.Group>

            <button type='submit' className='btn btn-lm-primary' disabled={busy || !content.trim()}>
              {busy ? <Spinner animation='border' size='sm' /> : t('feedback.submit')}
            </button>
          </Form>
        </CellGroup>
      </div>
    </Page>
  );
};

export default Feedback;
