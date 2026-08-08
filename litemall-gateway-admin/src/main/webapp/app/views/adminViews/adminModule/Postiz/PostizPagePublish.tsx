import { IPageSummary, useListPagesQuery } from 'app/shared/reducers/private/services/adminContentApi';
import {
  IPostizPreview,
  IPostizPublishResult,
  useGetPostizChannelsQuery,
  usePreviewPostizMutation,
  usePublishPostizMutation,
} from 'app/shared/reducers/private/services/postizApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { fmtDateTime } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import { categoryLabel, categoryTag } from 'app/views/adminViews/adminModule/Page/pageFormat';
import ChannelPicker, { channelLabel } from './ChannelPicker';
import { nextFullHourLocal, pageCommand, pageSignature, pageSourceNotes } from './postizSource';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Wave 20: DIY-page source for social publishing — ONE page promoted in ONE
// post per channel ({pageId, channelIds[], startTime} against the same
// /preview + /publish endpoints as the product flow). The page is picked from
// the DIY page list (active OR draft rows are selectable, but only an ACTIVE
// page publishes — the server resolves the page through the public read and
// refuses drafts). Groupon-category pages are REFUSED server-side with a
// typed errno; that errmsg — like every per-channel Postiz error — is shown
// VERBATIM.

const PostizPagePublish: React.FC = () => {
  const { data: channels = [], isLoading: channelsLoading, isError: channelsError } = useGetPostizChannelsQuery();

  // page picker
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(10);
  const [category, setCategory] = React.useState('');
  const [status, setStatus] = React.useState('');
  const { data: pagesData, isLoading: pagesLoading, isFetching: pagesFetching, isError: pagesError } = useListPagesQuery({
    page,
    limit,
    category: category as '' | 'general' | 'coupon' | 'groupon',
    status: status as '' | 'draft' | 'active',
  });

  const [selectedPage, setSelectedPage] = React.useState<IPageSummary | null>(null);
  const [channelIds, setChannelIds] = React.useState<Set<string>>(new Set());
  const [startLocal, setStartLocal] = React.useState(nextFullHourLocal);

  const [previewData, setPreviewData] = React.useState<IPostizPreview | null>(null);
  const [previewSig, setPreviewSig] = React.useState<string | null>(null);
  const [results, setResults] = React.useState<IPostizPublishResult[] | null>(null);
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [previewMutation, { isLoading: previewing }] = usePreviewPostizMutation();
  const [publishMutation, { isLoading: publishing }] = usePublishPostizMutation();

  const currentSig = pageSignature(selectedPage?.id ?? null, channelIds, startLocal);
  const inputsReady = selectedPage != null && channelIds.size > 0 && !!startLocal;
  const previewFresh = previewData != null && previewSig === currentSig;
  const notes = pageSourceNotes(selectedPage);

  const toggleChannel = (id: string) =>
    setChannelIds(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

  const onPreview = async () => {
    if (!selectedPage) return;
    setActionError(null);
    setResults(null);
    const res = await previewMutation(pageCommand(selectedPage.id, channelIds, startLocal));
    if ('error' in res && res.error) {
      setActionError(`Request failed (${(res.error as { status?: number | string }).status ?? 'network'}).`);
      return;
    }
    // The groupon-category refusal (and any other typed errno) lands here —
    // errmsg is shown verbatim.
    const msg = errnoMessage(res.data);
    if (msg) {
      setActionError(msg);
      setPreviewData(null);
      setPreviewSig(null);
      return;
    }
    setPreviewData(res.data?.data ?? { batch: [] });
    setPreviewSig(currentSig);
  };

  const onPublish = async () => {
    if (!previewFresh || !selectedPage) return;
    if (
      !window.confirm(
        `Schedule a post for page "${selectedPage.name}" across ${channelIds.size} channel${channelIds.size === 1 ? '' : 's'} at ${startLocal.replace('T', ' ')}?`
      )
    ) {
      return;
    }
    setActionError(null);
    const res = await publishMutation(pageCommand(selectedPage.id, channelIds, startLocal));
    if ('error' in res && res.error) {
      setActionError(`Request failed (${(res.error as { status?: number | string }).status ?? 'network'}).`);
      return;
    }
    const msg = errnoMessage(res.data);
    if (msg) {
      setActionError(msg);
      return;
    }
    setResults(res.data?.data?.results ?? []);
  };

  const onReset = () => {
    setSelectedPage(null);
    setChannelIds(new Set());
    setStartLocal(nextFullHourLocal());
    setPreviewData(null);
    setPreviewSig(null);
    setResults(null);
    setActionError(null);
  };

  const list = pagesData?.list ?? [];
  const total = pagesData?.total ?? 0;
  const pages = limit > 0 ? Math.ceil(total / limit) : 0;

  return (
    <>
      {/* 1 — page */}
      <h5 className='mt-3'>1 · Pick a page</h5>
      <div className='text-muted small mb-2'>
        One post per channel promoting the page at its public <code>/page/&lt;id&gt;</code> URL. Only ACTIVE pages can publish; groupon-category pages are
        refused for now.
      </div>
      <div className='filter-container'>
        <select
          className='form-select filter-item'
          style={{ width: 160 }}
          value={category}
          onChange={e => {
            setPage(1);
            setCategory(e.target.value);
          }}
          aria-label='Category'
        >
          <option value=''>All categories</option>
          <option value='general'>General</option>
          <option value='coupon'>Coupon</option>
          <option value='groupon'>Groupon</option>
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 140 }}
          value={status}
          onChange={e => {
            setPage(1);
            setStatus(e.target.value);
          }}
          aria-label='Status'
        >
          <option value=''>All statuses</option>
          <option value='active'>Active</option>
          <option value='draft'>Draft</option>
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
        {selectedPage && (
          <span className='filter-item'>
            Selected: <strong>{selectedPage.name}</strong>
          </span>
        )}
        {pagesFetching && <Spinner />}
      </div>

      {pagesError && <div className='alert alert-danger'>Failed to load pages.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th style={{ width: 34 }} />
            <th>Name</th>
            <th>Category</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {pagesLoading ? (
            <tr>
              <td colSpan={4} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={4} className='text-center text-muted py-5'>
                No pages match the current filters.
              </td>
            </tr>
          ) : (
            list.map(p => (
              <tr key={p.id}>
                <td>
                  <input
                    type='radio'
                    name='postiz-page'
                    className='form-check-input'
                    checked={selectedPage?.id === p.id}
                    onChange={() => setSelectedPage(p)}
                    aria-label={`Select ${p.name || p.id}`}
                  />
                </td>
                <td>
                  <Link to={`/admin/mall/page/${p.id}`}>{p.name || `#${p.id}`}</Link>
                  {p.isTemplate && (
                    <span className='ms-2'>
                      <Tag tag='primary'>Template</Tag>
                    </span>
                  )}
                </td>
                <td>
                  <Tag tag={categoryTag(p.category)}>{categoryLabel(p.category)}</Tag>
                </td>
                <td>{p.status === 'active' ? <Tag tag='success'>active</Tag> : <Tag tag='warning'>draft</Tag>}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>
      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={pagesFetching} onPage={setPage} />

      {notes.map(n => (
        <div key={n} className='alert alert-warning py-2'>
          {n}
        </div>
      ))}

      {/* 2 — channels */}
      <h5 className='mt-4'>2 · Pick channels</h5>
      <ChannelPicker channels={channels} loading={channelsLoading} error={channelsError} selected={channelIds} onToggle={toggleChannel} />

      {/* 3 — schedule */}
      <h5 className='mt-4'>3 · Schedule</h5>
      <div className='filter-container'>
        <label className='filter-item mb-0'>
          Post at{' '}
          <input type='datetime-local' className='form-control d-inline-block' style={{ width: 220 }} value={startLocal} onChange={e => setStartLocal(e.target.value)} />
        </label>
        <button className='btn btn-outline-primary filter-item' disabled={!inputsReady || previewing} onClick={onPreview}>
          {previewing ? 'Previewing…' : 'Preview'}
        </button>
        <button className='btn btn-primary filter-item' disabled={!previewFresh || publishing} onClick={onPublish}>
          {publishing ? 'Publishing…' : 'Publish'}
        </button>
        <button className='btn btn-outline-secondary filter-item' onClick={onReset}>
          Start over
        </button>
        {previewData != null && !previewFresh && <span className='filter-item text-warning'>Selection changed — preview again before publishing.</span>}
      </div>

      {/* server refusals (incl. the groupon-category one) land here VERBATIM */}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      {/* preview */}
      {previewData && !results && (
        <>
          <h5 className='mt-3'>Preview</h5>
          {(previewData.warnings ?? []).map(w => (
            <div key={w} className='alert alert-warning py-2'>
              {w}
            </div>
          ))}
          {previewData.batch.map((item, idx) => (
            <div key={item.pageId ?? item.goodsId ?? idx} className='card mb-3'>
              <div className='card-header d-flex align-items-center gap-2'>
                {item.picUrl && <img src={item.picUrl} alt='' style={{ width: 32, height: 32, objectFit: 'cover', borderRadius: 4 }} />}
                <strong>{item.name || selectedPage?.name || `Page #${item.pageId ?? ''}`}</strong>
                {item.pageId != null && (
                  <Link className='small' to={`/admin/mall/page/${item.pageId}`}>
                    Page #{item.pageId}
                  </Link>
                )}
                <span className='ms-auto'>{fmtDateTime(item.scheduleAt)}</span>
              </div>
              <div className='card-body'>
                {(item.warnings ?? []).map(w => (
                  <div key={w} className='mb-2'>
                    <Tag tag='warning'>{w}</Tag>
                  </div>
                ))}
                {(item.perChannel ?? []).map(pc => (
                  <div key={pc.integrationId} className='border rounded p-2 mb-2'>
                    <div className='small text-muted mb-1'>{channelLabel(channels, pc.integrationId)}</div>
                    {/* content is DOMPurify-sanitised server-side per the Wave-17 contract */}
                    <div dangerouslySetInnerHTML={{ __html: pc.content ?? '' }} />
                    {pc.settings && Object.keys(pc.settings).length > 0 && (
                      <code className='small d-block mt-1 text-muted'>{JSON.stringify(pc.settings)}</code>
                    )}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </>
      )}

      {/* publish results */}
      {results && (
        <>
          <h5 className='mt-3'>Publish results</h5>
          <table className='el-table'>
            <thead>
              <tr>
                <th>Page</th>
                <th>Scheduled</th>
                <th>Channel</th>
                <th>Result</th>
                <th>Postiz post</th>
              </tr>
            </thead>
            <tbody>
              {results.flatMap(r =>
                (r.channels ?? []).map((ch, i) => (
                  <tr key={`${r.pageId ?? r.goodsId ?? 'page'}-${ch.integrationId}`}>
                    {i === 0 && (
                      <>
                        <td rowSpan={r.channels?.length}>
                          {r.pageId != null ? (
                            <Link to={`/admin/mall/page/${r.pageId}`}>{selectedPage?.name || `Page #${r.pageId}`}</Link>
                          ) : (
                            selectedPage?.name || '—'
                          )}
                        </td>
                        <td rowSpan={r.channels?.length}>{fmtDateTime(r.scheduleAt)}</td>
                      </>
                    )}
                    <td>{channelLabel(channels, ch.integrationId)}</td>
                    <td>{ch.ok ? <Tag tag='success'>ok</Tag> : <Tag tag='danger'>failed{ch.error ? ` — ${ch.error}` : ''}</Tag>}</td>
                    <td>{ch.postizPostId ? <code>{ch.postizPostId}</code> : <span className='text-muted'>—</span>}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </>
      )}
    </>
  );
};

export default PostizPagePublish;
