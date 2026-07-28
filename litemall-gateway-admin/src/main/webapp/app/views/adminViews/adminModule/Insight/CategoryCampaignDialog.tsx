import {
  ICampaignPlatformStatus,
  useCreateCampaignFromCategoryMutation,
} from 'app/shared/reducers/private/services/adminPromotionApi';
import { PLATFORM_LABEL, SOCIAL_PLATFORMS, SocialPlatform } from 'app/shared/reducers/private/services/adminSocialApi';
import { ElTag, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Button, Modal } from 'react-bootstrap';
import { Link } from 'react-router-dom';

// Wave 12: "Launch category campaign" — POST /promotion/campaign/from-category
// creates the campaign row plus per-platform social_post drafts. While the
// Meta/TikTok tokens are absent the service answers with honest per-platform
// disabled/failed statuses; this dialog renders them verbatim (warning/danger
// tags), never a fake success.

interface Props {
  categoryId: number;
  categoryName?: string;
  onClose: () => void;
}

const statusTag = (status?: string): ElTag => {
  const s = (status || '').toLowerCase();
  if (s === 'posted' || s === 'ok' || s === 'success' || s === 'created' || s === 'draft') return s === 'draft' ? 'info' : 'success';
  if (s === 'disabled') return 'warning';
  if (s === 'failed' || s === 'error') return 'danger';
  return 'info';
};

const CategoryCampaignDialog: React.FC<Props> = ({ categoryId, categoryName, onClose }) => {
  const [createCampaign, { isLoading: saving }] = useCreateCampaignFromCategoryMutation();

  const [name, setName] = React.useState('');
  const [start, setStart] = React.useState('');
  const [stop, setStop] = React.useState('');
  const [platforms, setPlatforms] = React.useState<SocialPlatform[]>([...SOCIAL_PLATFORMS]);
  const [error, setError] = React.useState<string | null>(null);
  const [result, setResult] = React.useState<{ campaignId?: number; platforms: ICampaignPlatformStatus[] } | null>(null);

  const toggle = (p: SocialPlatform) => setPlatforms(cur => (cur.includes(p) ? cur.filter(x => x !== p) : [...cur, p]));

  const onSubmit = async () => {
    if (!start || !stop) {
      setError('Set the campaign schedule (start and stop).');
      return;
    }
    if (stop <= start) {
      setError('Stop must be after start.');
      return;
    }
    if (platforms.length === 0) {
      setError('Pick at least one platform.');
      return;
    }
    setError(null);
    const res = await createCampaign({
      categoryL1Id: categoryId,
      name: name.trim() || undefined,
      schedule: { start, stop },
      platforms,
    });
    if ('error' in res && res.error) {
      const err = res.error as { status?: number | string; data?: { message?: string } };
      setError(err.data?.message || `Request failed${err.status ? ` (${err.status})` : ''}.`);
      return;
    }
    setResult({ campaignId: res.data?.campaignId, platforms: res.data?.platforms ?? [] });
  };

  return (
    <Modal show onHide={onClose} centered>
      <Modal.Header closeButton>
        <Modal.Title as='h5'>Launch campaign — {categoryName || `category #${categoryId}`}</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {result ? (
          <>
            <div className='alert alert-success'>
              Campaign {result.campaignId != null ? `#${result.campaignId} ` : ''}created with its platform drafts. Manage it in the{' '}
              <Link to='/admin/promotion/campaign' onClick={onClose}>
                Campaigns panel
              </Link>{' '}
              and the drafts on the{' '}
              <Link to='/admin/promotion/social' onClick={onClose}>
                Social posts page
              </Link>
              .
            </div>
            {result.platforms.length > 0 ? (
              <ul className='list-unstyled mb-0'>
                {result.platforms.map((p, i) => (
                  <li key={p.platform ?? i} className='mb-1'>
                    <Tag tag={statusTag(p.status)}>{p.status || 'unknown'}</Tag>{' '}
                    <strong>{PLATFORM_LABEL[p.platform as SocialPlatform] ?? p.platform ?? 'platform'}</strong>
                    {p.postId != null && <span className='text-muted small'> · post #{p.postId}</span>}
                    {(p.message || p.reason) && <span className='text-muted small'> — {p.message || p.reason}</span>}
                  </li>
                ))}
              </ul>
            ) : (
              <div className='text-muted small'>No per-platform statuses returned.</div>
            )}
          </>
        ) : (
          <>
            {error && <div className='alert alert-danger'>{error}</div>}
            <div className='mb-2'>
              <label className='form-label'>
                Name <span className='text-muted small'>(optional)</span>
              </label>
              <input className='form-control' value={name} onChange={e => setName(e.target.value)} placeholder={`${categoryName || 'Category'} campaign`} />
            </div>
            <div className='row'>
              <div className='col mb-2'>
                <label className='form-label'>Start</label>
                <input type='datetime-local' className='form-control' value={start} onChange={e => setStart(e.target.value)} />
              </div>
              <div className='col mb-2'>
                <label className='form-label'>Stop</label>
                <input type='datetime-local' className='form-control' value={stop} onChange={e => setStop(e.target.value)} />
              </div>
            </div>
            <div className='mb-1'>
              <label className='form-label d-block'>Platforms</label>
              {SOCIAL_PLATFORMS.map(p => (
                <label key={p} className='form-check form-check-inline'>
                  <input className='form-check-input' type='checkbox' checked={platforms.includes(p)} onChange={() => toggle(p)} />
                  <span className='form-check-label'>{PLATFORM_LABEL[p]}</span>
                </label>
              ))}
              <div className='text-muted small'>Disabled adapters answer with an honest failed/disabled draft — no fake posting.</div>
            </div>
          </>
        )}
      </Modal.Body>
      <Modal.Footer>
        <Button variant='secondary' onClick={onClose}>
          {result ? 'Close' : 'Cancel'}
        </Button>
        {!result && (
          <Button variant='primary' disabled={saving} onClick={onSubmit}>
            {saving ? 'Creating…' : 'Create campaign'}
          </Button>
        )}
      </Modal.Footer>
    </Modal>
  );
};

export default CategoryCampaignDialog;
