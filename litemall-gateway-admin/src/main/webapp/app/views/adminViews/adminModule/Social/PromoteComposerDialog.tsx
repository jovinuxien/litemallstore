import {
  IPlatformAvailability,
  IPlatformPostResult,
  PLATFORM_LABEL,
  SocialPlatform,
  useComposePreviewQuery,
  usePostSocialMutation,
} from 'app/shared/reducers/private/services/adminSocialApi';
import * as React from 'react';
import { Button, Modal } from 'react-bootstrap';
import { Link } from 'react-router-dom';

// Wave 6: "Promote" composer dialog, opened from the goods list/detail. Loads
// the templated compose-preview from promotion-service, lets the admin edit
// the caption, pick the media and the target platforms (TikTok is gated on
// the goods having a video; disabled adapters stay selectable — the service
// answers with an honest per-platform `failed` result), shows a preview pane,
// and posts. Per-platform results render inline; the ledger lives on the
// Social posts page (/admin/promotion/social).

interface Props {
  goodsId: number;
  goodsName?: string;
  onClose: () => void;
}

// A platform checkbox is blocked when the goods can't be posted there at all
// (e.g. TikTok without a video), and TikTok is additionally gated while its
// adapter is disabled (per the Wave-6 block). Meta platforms stay selectable
// with a disabled adapter so posting still writes an honest `failed` ledger
// row — the acceptance's creds-less round-trip.
const gateReason = (p: IPlatformAvailability): string | undefined => {
  if (p.available === false) return p.reason || 'Not available for this goods.';
  if (p.platform === 'tiktok' && p.enabled === false) return p.reason || 'TikTok adapter is disabled.';
  return undefined;
};

const hintReason = (p: IPlatformAvailability): string | undefined => {
  if (p.enabled === false) return p.reason || 'Adapter disabled — posting records a failed ledger row.';
  return undefined;
};

const PromoteComposerDialog: React.FC<Props> = ({ goodsId, goodsName, onClose }) => {
  const { data: preview, isLoading, isError, error } = useComposePreviewQuery(goodsId);
  const [postSocial, { isLoading: posting }] = usePostSocialMutation();

  const [caption, setCaption] = React.useState('');
  const [mediaUrl, setMediaUrl] = React.useState('');
  const [selected, setSelected] = React.useState<SocialPlatform[]>([]);
  const [results, setResults] = React.useState<IPlatformPostResult[] | null>(null);
  const [postError, setPostError] = React.useState<string | null>(null);

  // Prefill once the preview arrives: templated caption, first image, and
  // every platform the goods can be posted to.
  React.useEffect(() => {
    if (!preview) return;
    setCaption(preview.caption ?? '');
    setMediaUrl(preview.images[0] ?? preview.videoUrl ?? '');
    setSelected(preview.platforms.filter(p => !gateReason(p)).map(p => p.platform));
  }, [preview]);

  const toggle = (platform: SocialPlatform) =>
    setSelected(sel => (sel.includes(platform) ? sel.filter(p => p !== platform) : [...sel, platform]));

  const onPost = async () => {
    setPostError(null);
    setResults(null);
    const res = await postSocial({ goodsId, caption, mediaUrl: mediaUrl || undefined, platforms: selected });
    if ('error' in res && res.error) {
      const status = (res.error as { status?: number | string }).status;
      const message = (res.error as { data?: { message?: string } }).data?.message;
      setPostError(message || `Post failed (${status ?? 'network'}).`);
      return;
    }
    setResults(res.data ?? []);
  };

  const errStatus = (error as { status?: number | string })?.status;
  const mediaOptions = preview ? preview.images : [];
  const isVideoSelected = !!preview?.videoUrl && mediaUrl === preview.videoUrl;

  return (
    <Modal show onHide={onClose} size='lg' centered>
      <Modal.Header closeButton>
        <Modal.Title>Promote {goodsName || `goods #${goodsId}`}</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {isLoading ? (
          <div className='text-center p-5'>
            <span className='spinner-border text-primary' role='status' />
          </div>
        ) : isError || !preview ? (
          <div className='alert alert-danger'>Failed to load compose preview{errStatus ? ` (${errStatus})` : ''}.</div>
        ) : (
          <div className='row g-3'>
            <div className='col-md-7'>
              <label className='form-label fw-bold' htmlFor='promote-caption'>
                Caption
              </label>
              <textarea
                id='promote-caption'
                className='form-control'
                rows={5}
                value={caption}
                onChange={e => setCaption(e.target.value)}
              />

              <div className='form-label fw-bold mt-3'>Media</div>
              {mediaOptions.length === 0 && !preview.videoUrl ? (
                <div className='text-muted small'>This goods has no images or video.</div>
              ) : (
                <div className='d-flex flex-wrap gap-2'>
                  {mediaOptions.map(url => (
                    <button
                      key={url}
                      type='button'
                      className={`btn p-0 border ${mediaUrl === url ? 'border-primary border-2' : 'border-light'}`}
                      onClick={() => setMediaUrl(url)}
                      title={url}
                    >
                      <img src={url} alt='' style={{ width: 72, height: 72, objectFit: 'cover' }} />
                    </button>
                  ))}
                  {preview.videoUrl && (
                    <button
                      type='button'
                      className={`btn btn-sm align-self-center ${isVideoSelected ? 'btn-primary' : 'btn-outline-secondary'}`}
                      onClick={() => setMediaUrl(preview.videoUrl as string)}
                      title={preview.videoUrl}
                    >
                      ▶ Video
                    </button>
                  )}
                </div>
              )}

              <div className='form-label fw-bold mt-3'>Platforms</div>
              {preview.platforms.map(p => {
                const gated = gateReason(p);
                const hint = hintReason(p);
                return (
                  <div key={p.platform} className='form-check' title={gated ?? hint}>
                    <input
                      className='form-check-input'
                      type='checkbox'
                      id={`platform-${p.platform}`}
                      disabled={!!gated}
                      checked={!gated && selected.includes(p.platform)}
                      onChange={() => toggle(p.platform)}
                    />
                    <label className={`form-check-label ${gated ? 'text-muted' : ''}`} htmlFor={`platform-${p.platform}`}>
                      {PLATFORM_LABEL[p.platform]}
                      {gated && <span className='small ms-1'>({gated})</span>}
                      {!gated && hint && (
                        <span className='badge text-bg-warning ms-1' title={hint}>
                          adapter disabled
                        </span>
                      )}
                    </label>
                  </div>
                );
              })}
            </div>

            <div className='col-md-5'>
              <div className='form-label fw-bold'>Preview</div>
              <div className='border rounded p-2 bg-light'>
                {isVideoSelected ? (
                  // eslint-disable-next-line jsx-a11y/media-has-caption
                  <video src={mediaUrl} controls style={{ width: '100%', maxHeight: 220 }} />
                ) : mediaUrl ? (
                  <img src={mediaUrl} alt='preview' style={{ width: '100%', maxHeight: 220, objectFit: 'contain' }} />
                ) : (
                  <div className='text-muted small p-4 text-center'>No media selected</div>
                )}
                <div className='mt-2 small' style={{ whiteSpace: 'pre-wrap' }}>
                  {caption || <span className='text-muted'>Empty caption</span>}
                </div>
                {preview.shareUrl && (
                  <div className='mt-1 small text-truncate'>
                    <a href={preview.shareUrl} target='_blank' rel='noreferrer'>
                      {preview.shareUrl}
                    </a>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {postError && <div className='alert alert-danger mt-3 mb-0'>{postError}</div>}
        {results && (
          <div className='mt-3'>
            {results.length === 0 && <div className='alert alert-warning mb-0'>The service reported no per-platform results.</div>}
            {results.map((r, i) => (
              <div
                key={r.platform ?? i}
                className={`alert py-2 mb-2 ${r.success ? 'alert-success' : 'alert-danger'}`}
                role='status'
              >
                <strong>{r.platform ? PLATFORM_LABEL[r.platform] : 'Platform'}</strong>: {r.success ? 'posted' : 'failed'}
                {r.error && <span className='ms-1'>— {r.error}</span>}
              </div>
            ))}
            <div className='small text-muted'>
              Ledger rows were recorded — see <Link to='/admin/promotion/social'>Social posts</Link> to retry failures.
            </div>
          </div>
        )}
      </Modal.Body>
      <Modal.Footer>
        <Button variant='secondary' onClick={onClose}>
          Close
        </Button>
        <Button variant='primary' disabled={isLoading || posting || !caption.trim() || selected.length === 0} onClick={onPost}>
          {posting ? 'Posting…' : `Post to ${selected.length} platform${selected.length === 1 ? '' : 's'}`}
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default PromoteComposerDialog;
