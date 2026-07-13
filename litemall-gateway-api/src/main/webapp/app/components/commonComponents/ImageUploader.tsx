import React, { useRef, useState } from 'react';
import { Form, Spinner } from 'react-bootstrap';

import { isMissingEndpoint, userApi } from 'app/shared/api';

/**
 * Shared image uploader: file picker → `POST /srv/storage/upload`
 * (goods-management Wave 4 — requires login, 5 MB cap, image-only magic-byte
 * whitelist) with thumbnail previews + remove. Until that endpoint merges and
 * runs, isMissingEndpoint flips the control to a comma-separated URL text
 * input (the previous ReviewForm pattern) so forms stay usable.
 */
interface Props {
  value: string[];
  onChange: (urls: string[]) => void;
  /** Max number of images (default 5). */
  max?: number;
  disabled?: boolean;
}

const ImageUploader: React.FC<Props> = ({ value, onChange, max = 5, disabled }) => {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Fallback when /srv/storage/upload is not live yet (goods-management Wave 4 merge).
  const [urlMode, setUrlMode] = useState(false);
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
        setError('Upload returned no URL.');
      }
    } catch (err) {
      if (isMissingEndpoint(err)) {
        setUrlMode(true);
      } else {
        setError('Image upload failed (max 5 MB, images only).');
      }
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  if (urlMode) {
    return (
      <>
        <Form.Control
          size='sm'
          placeholder='Image URLs (optional, comma-separated)'
          value={value.join(', ')}
          onChange={e =>
            onChange(
              e.target.value
                .split(/[\n,]/)
                .map(s => s.trim())
                .filter(Boolean)
                .slice(0, max)
            )
          }
          disabled={disabled}
        />
        <Form.Text className='text-muted'>Image upload isn’t available yet — paste image URLs.</Form.Text>
      </>
    );
  }

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
                aria-label='Remove image'
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
