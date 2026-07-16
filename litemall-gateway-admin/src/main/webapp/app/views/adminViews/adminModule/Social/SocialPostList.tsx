import {
  ISocialPost,
  PLATFORM_LABEL,
  SOCIAL_PLATFORMS,
  SocialPlatform,
  SocialPostStatus,
  socialOpMessage,
  useListSocialPostsQuery,
  useRetrySocialPostMutation,
} from 'app/shared/reducers/private/services/adminSocialApi';
import { ElTag, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Wave 6: social-posting ledger, authenticated admin → promotion-service
// /srv/private/admin/social/list. Platform + status filters, error tooltip on
// failed rows, `auto` badge for the deal auto-poster, Retry on failed rows
// (guarded server-side), and an external link-out when the platform post id
// resolves to a public URL.

const STATUS_TAG: Record<SocialPostStatus, { tag: ElTag; text: string }> = {
  draft: { tag: 'info', text: 'draft' },
  posted: { tag: 'success', text: 'posted' },
  failed: { tag: 'danger', text: 'failed' },
};

const STATUSES: SocialPostStatus[] = ['draft', 'posted', 'failed'];

// Best-effort public URL for an external post id: absolute URLs pass through;
// Facebook's `{pageId}_{postId}` ids resolve via facebook.com. IG media ids
// and TikTok video ids don't map to a public URL without extra context, so
// those render as plain text (reconcile with handoff-social-composer.md if
// the service starts shipping permalinks).
const externalUrl = (p: ISocialPost): string | undefined => {
  if (!p.externalPostId) return undefined;
  if (/^https?:\/\//.test(p.externalPostId)) return p.externalPostId;
  if (p.platform === 'meta_fb') return `https://www.facebook.com/${p.externalPostId}`;
  return undefined;
};

const SocialPostList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [status, setStatus] = React.useState<SocialPostStatus | ''>('');
  const [platform, setPlatform] = React.useState<SocialPlatform | ''>('');

  const { data, isLoading, isFetching, isError, error } = useListSocialPostsQuery({ page, limit, status, platform });
  const [retryPost, { isLoading: retrying }] = useRetrySocialPostMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const errStatus = (error as { status?: number | string })?.status;

  const onRetry = async (post: ISocialPost) => {
    if (post.id == null) return;
    setActionError(null);
    const res = await retryPost(post.id);
    setActionError(socialOpMessage(res));
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <select
          className='form-select filter-item'
          style={{ width: 150 }}
          value={platform}
          onChange={e => {
            setPage(1);
            setPlatform(e.target.value as SocialPlatform | '');
          }}
          aria-label='Platform filter'
        >
          <option value=''>All platforms</option>
          {SOCIAL_PLATFORMS.map(p => (
            <option key={p} value={p}>
              {PLATFORM_LABEL[p]}
            </option>
          ))}
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 140 }}
          value={status}
          onChange={e => {
            setPage(1);
            setStatus(e.target.value as SocialPostStatus | '');
          }}
          aria-label='Status filter'
        >
          <option value=''>All statuses</option>
          {STATUSES.map(s => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 120 }}
          value={limit}
          onChange={e => {
            setPage(1);
            setLimit(Number(e.target.value));
          }}
          aria-label='Page size'
        >
          {PAGE_SIZES.map(n => (
            <option key={n} value={n}>
              {n} / page
            </option>
          ))}
        </select>
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load social posts{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Goods</th>
            <th>Platform</th>
            <th>Caption</th>
            <th>Status</th>
            <th>Posted by</th>
            <th>External post</th>
            <th>Time</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={8} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={8} className='text-center text-muted py-5'>
                No social posts found. Use the Promote button on a goods to compose one.
              </td>
            </tr>
          ) : (
            list.map(p => {
              const st = p.status ? STATUS_TAG[p.status] : undefined;
              const link = externalUrl(p);
              return (
                <tr key={p.id}>
                  <td>{p.goodsId != null ? <Link to={`/admin/goods/${p.goodsId}`}>#{p.goodsId}</Link> : '—'}</td>
                  <td>
                    <Tag tag='primary'>{p.platform ? PLATFORM_LABEL[p.platform] : '—'}</Tag>
                  </td>
                  <td style={{ maxWidth: 320 }} className='text-truncate' title={p.caption}>
                    {p.caption || '—'}
                  </td>
                  <td>
                    <span title={p.status === 'failed' ? p.error : undefined}>
                      {st ? <Tag tag={st.tag}>{st.text}</Tag> : <Tag tag='info'>{p.status ?? 'unknown'}</Tag>}
                    </span>
                    {p.status === 'failed' && p.error && (
                      <div className='small text-danger text-truncate' style={{ maxWidth: 200 }} title={p.error}>
                        {p.error}
                      </div>
                    )}
                  </td>
                  <td>
                    {p.postedBy === 'auto' ? <Tag tag='warning'>auto</Tag> : p.postedBy || '—'}
                  </td>
                  <td style={{ maxWidth: 180 }} className='text-truncate'>
                    {link ? (
                      <a href={link} target='_blank' rel='noreferrer' title={p.externalPostId}>
                        View post ↗
                      </a>
                    ) : (
                      <span title={p.externalPostId}>{p.externalPostId || '—'}</span>
                    )}
                  </td>
                  <td className='small'>{(p.updateTime ?? p.addTime)?.replace('T', ' ') || '—'}</td>
                  <td className='text-end'>
                    {p.status === 'failed' && (
                      <button className='btn btn-sm btn-outline-primary' disabled={retrying} onClick={() => onRetry(p)}>
                        Retry
                      </button>
                    )}
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={data?.pages} total={data?.total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default SocialPostList;
