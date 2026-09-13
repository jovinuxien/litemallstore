import React, { useRef, useState } from 'react';
import { Form, Spinner } from 'react-bootstrap';

import { userApi } from 'app/shared/api';
import { useTranslation } from 'app/i18n';

/**
 * Shared image uploader: file picker → `POST /srv/storage/upload`
 * (goods-management Wave 4 — requires login, 5 MB cap, image-only magic-byte
 * whitelist) with thumbnail previews + remove.
 */
interface Props {
  value: string[];
  onChange: (urls: string[]) => void;
  /** Max number of images (default 5). */
  max?: number;
  disabled?: boolean;
}

const ImageUploader: React.FC<Props> = ({ value, onChange, max = 5, disabled }) => {
  const { t } = useTranslation();
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const pick = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const stored = await userApi.storageUpload(file);
      const url = stored?.url;
      if (url) {
        onChange([...value, url].slice(0, max));
      } else {
        setError(t('forms.upload.noUrl'));
      }
    } catch {
      setError(t('forms.upload.failed'));
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  return (
    <div>
      {value.length > 0 && (
        <div className='d-flex gap-2 flex-wrap mb-2'>
          {value.map(url => (
            <div key={url} style={{ position: 'relative' }}>
              <img src={url} alt='' style={{ width: 64, height: 64, objectFit: 'cover', borderRadius: 6 }} />
              <button
                type='button'
                className='btn-close btn-close-white'
                aria-label={t('forms.upload.remove')}
                style={{
                  position: 'absolute',
                  top: 2,
                  right: 2,
                  fontSize: 10,
                  backgroundColor: 'rgba(0,0,0,.5)',
                  borderRadius: '50%',
                  padding: 4,
                }}
                onClick={() => onChange(value.filter(u => u !== url))}
              />
            </div>
          ))}
        </div>
      )}
      {value.length < max && (
        <Form.Control ref={fileRef} type='file' size='sm' accept='image/*' onChange={pick} disabled={disabled || uploading} />
      )}
      {uploading && (
        <Form.Text className='text-muted'>
          <Spinner animation='border' size='sm' /> Uploading…
        </Form.Text>
      )}
      {error && <Form.Text className='text-danger'>{error}</Form.Text>}
    </div>
  );
};

export default ImageUploader;
