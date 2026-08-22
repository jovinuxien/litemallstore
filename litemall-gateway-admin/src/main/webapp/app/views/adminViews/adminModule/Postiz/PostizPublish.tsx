import {
  IPostizPreview,
  IPostizPublishResult,
  PostizBatchCommand,
  useGetPostizChannelsQuery,
  useGetPostizStatusQuery,
  usePreviewPostizMutation,
  usePublishPostizMutation,
} from 'app/shared/reducers/private/services/postizApi';
import { InsightSortKey, useGetInsightCategoriesQuery, useGetInsightGoodsListQuery } from 'app/shared/reducers/private/services/insightApi';
import { PAGE_SIZES, Pagination, Spinner, Tag, errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { fmtDateTime, fmtMoney, fmtPct } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import ChannelPicker, { channelLabel } from './ChannelPicker';
import PostizHistory from './PostizHistory';
import PostizPagePublish from './PostizPagePublish';
import { nextFullHourLocal } from './postizSource';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Wave 17: "Social publishing" — admin-picked product posts scheduled across
// Postiz channels (promotion-service backend, Wave-17 CONTRACT). Compose flow:
// category → product multi-select (cap 25, the contract's batch cap) →
// channel checkboxes → start + interval → Preview (zero side effects) →
// Publish → per-product/per-channel results, Postiz errors VERBATIM. The
// panel is menu-hidden unless /postiz/status says enabled; deep links land on
// an honest "not configured" note instead.
// Wave 20 adds a SOURCE picker: Products (this flow, unchanged) | DIY page
// (PostizPagePublish — one page per post, server refusals shown verbatim).

const MAX_BATCH = 25;

const SORT_OPTIONS: { key: InsightSortKey; label: string }[] = [
  { key: 'add_time', label: 'Arrival date' },
  { key: 'retail_price', label: 'Price' },
  { key: 'stock', label: 'Stock' },
  { key: 'margin_pct', label: 'Margin' },
  { key: 'sales', label: 'Sales' },
];

interface SelectedGoods {
  name?: string;
  picUrl?: string;
}

// The preview/publish inputs that must not drift between Preview and Publish.
const batchSignature = (goodsIds: number[], channelIds: string[], start: string, interval: number): string =>
  JSON.stringify([[...goodsIds].sort((a, b) => a - b), [...channelIds].sort(), start, interval]);

const PostizCompose: React.FC = () => {
  const { data: channels = [], isLoading: channelsLoading, isError: channelsError } = useGetPostizChannelsQuery();
  const { data: categories } = useGetInsightCategoriesQuery();

  // product picker
  const [categoryId, setCategoryId] = React.useState('');
  const [sort, setSort] = React.useState<InsightSortKey>('add_time');
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(10);
  const { data: goods, isLoading: goodsLoading, isFetching: goodsFetching } = useGetInsightGoodsListQuery(
    { categoryId, sort, order: 'desc', page, limit },
    { skip: !categoryId }
  );

  // batch inputs
  const [selected, setSelected] = React.useState<Map<number, SelectedGoods>>(new Map());
  const [channelIds, setChannelIds] = React.useState<Set<string>>(new Set());
  const [startLocal, setStartLocal] = React.useState(nextFullHourLocal);
  const [intervalMinutes, setIntervalMinutes] = React.useState(30);

  // preview / publish state
  const [previewData, setPreviewData] = React.useState<IPostizPreview | null>(null);
  const [previewSig, setPreviewSig] = React.useState<string | null>(null);
  const [results, setResults] = React.useState<IPostizPublishResult[] | null>(null);
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [previewMutation, { isLoading: previewing }] = usePreviewPostizMutation();
  const [publishMutation, { isLoading: publishing }] = usePublishPostizMutation();

  const goodsIds = React.useMemo(() => [...selected.keys()], [selected]);
  const currentSig = batchSignature(goodsIds, [...channelIds], startLocal, intervalMinutes);
  const inputsReady = goodsIds.length > 0 && channelIds.size > 0 && !!startLocal && intervalMinutes >= 1;
  const previewFresh = previewData != null && previewSig === currentSig;

  // "posted N days ago" dedup badges, keyed by goodsId from the last preview.
  const previewWarnings = React.useMemo(() => {
    const m = new Map<number, string[]>();
    for (const item of previewData?.batch ?? []) {
      if (item.goodsId != null && item.warnings?.length) m.set(item.goodsId, item.warnings);
    }
    return m;
  }, [previewData]);

  const toggleGoods = (id: number, row: SelectedGoods) =>
    setSelected(prev => {
      const next = new Map(prev);
      if (next.has(id)) next.delete(id);
      else if (next.size < MAX_BATCH) next.set(id, row);
      return next;
    });

  const toggleChannel = (id: string) =>
    setChannelIds(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

  const buildBody = (): PostizBatchCommand => ({
    goodsIds,
    channelIds: [...channelIds],
    startTime: new Date(startLocal).toISOString(),
    intervalMinutes,
  });

  const onPreview = async () => {
    setActionError(null);
    setResults(null);
    const res = await previewMutation(buildBody());
    if ('error' in res && res.error) {
      setActionError(`Request failed (${(res.error as { status?: number | string }).status ?? 'network'}).`);
      return;
    }
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
    if (!previewFresh) return;
    if (
      !window.confirm(
        `Schedule ${goodsIds.length} product post${goodsIds.length === 1 ? '' : 's'} across ${channelIds.size} channel${channelIds.size === 1 ? '' : 's'}, starting ${startLocal.replace('T', ' ')} (${intervalMinutes} min apart)?`
      )
    ) {
      return;
    }
    setActionError(null);
    const res = await publishMutation(buildBody());
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
    setSelected(new Map());
    setChannelIds(new Set());
    setStartLocal(nextFullHourLocal());
    setIntervalMinutes(30);
    setPreviewData(null);
    setPreviewSig(null);
    setResults(null);
    setActionError(null);
  };

  const list = goods?.list ?? [];
  const total = goods?.total ?? 0;
  const pages = goods?.pages ?? (limit > 0 ? Math.ceil(total / limit) : 0);

  return (
    <>
      {/* 1 — products */}
      <h5 className='mt-3'>1 · Pick products</h5>
      <div className='filter-container'>
        <select
          className='form-select filter-item'
          style={{ width: 280 }}
          value={categoryId}
          onChange={e => {
            setPage(1);
            setCategoryId(e.target.value);
          }}
          aria-label='Category'
        >
          <option value=''>Choose a category…</option>
          {(categories?.list ?? []).map(c => (
            <option key={c.categoryId} value={c.categoryId}>
              {c.name} ({c.onSaleCount} on sale)
            </option>
          ))}
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 180 }}
          value={sort}
          onChange={e => {
            setPage(1);
            setSort(e.target.value as InsightSortKey);
          }}
          aria-label='Sort by'
        >
          {SORT_OPTIONS.map(o => (
            <option key={o.key} value={o.key}>
              Sort: {o.label}
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
        <span className='filter-item text-muted'>
          {selected.size}/{MAX_BATCH} selected
        </span>
        {goodsFetching && <Spinner />}
      </div>

      {selected.size > 0 && (
        <div className='mb-2 d-flex flex-wrap gap-1'>
          {[...selected.entries()].map(([id, g]) => (
            <button key={id} type='button' className='btn btn-sm btn-outline-secondary' onClick={() => toggleGoods(id, g)} title='Remove from batch'>
              {g.name || `#${id}`} ✕
            </button>
          ))}
        </div>
      )}

      {!categoryId ? (
        <div className='text-muted py-4'>Choose a category to browse its products.</div>
      ) : (
        <>
          <table className='el-table'>
            <thead>
              <tr>
                <th style={{ width: 34 }} />
                <th>Goods</th>
                <th className='text-end'>Retail</th>
                <th className='text-end'>Margin</th>
                <th>Deal</th>
                <th>Posted</th>
              </tr>
            </thead>
            <tbody>
              {goodsLoading ? (
                <tr>
                  <td colSpan={6} className='text-center p-5'>
                    <span className='spinner-border text-primary' role='status' />
                  </td>
                </tr>
              ) : list.length === 0 ? (
                <tr>
                  <td colSpan={6} className='text-center text-muted py-5'>
                    No goods in this category.
                  </td>
                </tr>
              ) : (
                list.map(g => {
                  const checked = selected.has(g.id);
                  const warnings = previewWarnings.get(g.id);
                  return (
                    <tr key={g.id}>
                      <td>
                        <input
                          type='checkbox'
                          className='form-check-input'
                          checked={checked}
                          disabled={!checked && selected.size >= MAX_BATCH}
                          onChange={() => toggleGoods(g.id, { name: g.name, picUrl: g.picUrl })}
                          aria-label={`Select ${g.name || g.id}`}
                        />
                      </td>
                      <td>
                        {g.picUrl && <img src={g.picUrl} alt='' style={{ width: 40, height: 40, objectFit: 'cover', borderRadius: 4 }} className='me-2' />}
                        <Link to={`/admin/goods/${g.id}/insight`}>{g.name || `Goods #${g.id}`}</Link>
                        <span className='text-muted small ms-1'>#{g.id}</span>
                      </td>
                      <td className='text-end'>{fmtMoney(g.retailPrice)}</td>
                      <td className='text-end'>{fmtPct(g.marginPct)}</td>
                      <td>{g.dealStatus ? <Tag tag={g.dealStatus === 'live' ? 'danger' : 'primary'}>{g.dealStatus}</Tag> : <span className='text-muted'>—</span>}</td>
                      <td>
                        {warnings ? (
                          warnings.map(w => (
                            <Tag key={w} tag='warning'>
                              {w}
                            </Tag>
                          ))
                        ) : (
                          <span className='text-muted'>—</span>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
          <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={goodsFetching} onPage={setPage} />
        </>
      )}

      {/* 2 — channels */}
      <h5 className='mt-4'>2 · Pick channels</h5>
      <ChannelPicker channels={channels} loading={channelsLoading} error={channelsError} selected={channelIds} onToggle={toggleChannel} />

      {/* 3 — schedule */}
      <h5 className='mt-4'>3 · Schedule</h5>
      <div className='filter-container'>
        <label className='filter-item mb-0'>
          First post at{' '}
          <input type='datetime-local' className='form-control d-inline-block' style={{ width: 220 }} value={startLocal} onChange={e => setStartLocal(e.target.value)} />
        </label>
        <label className='filter-item mb-0'>
          then every{' '}
          <input
            type='number'
            min={1}
            className='form-control d-inline-block'
            style={{ width: 90 }}
            value={intervalMinutes}
            onChange={e => setIntervalMinutes(Number(e.target.value))}
          />{' '}
          minutes
        </label>
        <button className='btn btn-outline-primary filter-item' disabled={!inputsReady || previewing} onClick={onPreview}>
          {previewing ? 'Previewing…' : 'Preview'}
        </button>
        <button className='btn btn-primary filter-item' disabled={!previewFresh || publishing} onClick={onPublish}>
          {publishing ? 'Publishing…' : 'Publish'}
        </button>
        <button className='btn btn-outline-secondary filter-item' onClick={onReset}>
          Start new batch
        </button>
        {previewData != null && !previewFresh && <span className='filter-item text-warning'>Selection changed — preview again before publishing.</span>}
      </div>

      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      {/* preview cards */}
      {previewData && !results && (
        <>
          <h5 className='mt-3'>Preview</h5>
          {(previewData.warnings ?? []).map(w => (
            <div key={w} className='alert alert-warning py-2'>
              {w}
            </div>
          ))}
          {previewData.batch.map((item, idx) => (
            <div key={item.goodsId ?? idx} className='card mb-3'>
              <div className='card-header d-flex align-items-center gap-2'>
                {item.picUrl && <img src={item.picUrl} alt='' style={{ width: 32, height: 32, objectFit: 'cover', borderRadius: 4 }} />}
                <strong>{item.name || `Goods #${item.goodsId ?? '?'}`}</strong>
                {item.goodsId != null && <span className='text-muted small'>#{item.goodsId}</span>}
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
                <th>Goods</th>
                <th>Scheduled</th>
                <th>Channel</th>
                <th>Result</th>
                <th>Postiz post</th>
              </tr>
            </thead>
            <tbody>
              {results.flatMap(r =>
                (r.channels ?? []).map((ch, i) => (
                  <tr key={`${r.goodsId}-${ch.integrationId}`}>
                    {i === 0 && (
                      <>
                        <td rowSpan={r.channels?.length}>{(r.goodsId != null && selected.get(r.goodsId)?.name) || `Goods #${r.goodsId ?? '?'}`}</td>
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

const PostizPublish: React.FC = () => {
  const { data: status, isLoading } = useGetPostizStatusQuery();
  const [tab, setTab] = React.useState<'compose' | 'history'>('compose');
  // Wave 20 source picker: Products (Wave-17 flow, unchanged) | DIY page.
  const [source, setSource] = React.useState<'products' | 'page'>('products');

  if (isLoading) {
    return (
      <div className='app-container text-center p-5'>
        <span className='spinner-border text-primary' role='status' />
      </div>
    );
  }

  if (!status?.enabled) {
    return (
      <div className='app-container'>
        <h4>Social publishing</h4>
        <div className='alert alert-info'>
          Postiz publishing is not configured on this deployment. Set <code>LITEMALL_POSTIZ_BASE_URL</code> and <code>LITEMALL_POSTIZ_API_KEY</code> on
          promotion-service to enable it.
        </div>
      </div>
    );
  }

  return (
    <div className='app-container'>
      <div className='d-flex align-items-center'>
        <h4 className='mb-0'>Social publishing</h4>
        {status.channelCount != null && <span className='text-muted ms-2'>{status.channelCount} channels connected</span>}
      </div>
      <ul className='nav nav-tabs mt-3 mb-2'>
        {(
          [
            { value: 'compose', label: 'Compose' },
            { value: 'history', label: 'History' },
          ] as const
        ).map(t => (
          <li className='nav-item' key={t.value}>
            <button className={`nav-link${tab === t.value ? ' active' : ''}`} onClick={() => setTab(t.value)}>
              {t.label}
            </button>
          </li>
        ))}
      </ul>
      {tab === 'compose' ? (
        <>
          <div className='btn-group mt-2' role='group' aria-label='Post source'>
            <button
              type='button'
              className={`btn btn-sm ${source === 'products' ? 'btn-primary' : 'btn-outline-primary'}`}
              onClick={() => setSource('products')}
            >
              Products
            </button>
            <button
              type='button'
              className={`btn btn-sm ${source === 'page' ? 'btn-primary' : 'btn-outline-primary'}`}
              onClick={() => setSource('page')}
            >
              DIY page
            </button>
          </div>
          {source === 'products' ? <PostizCompose /> : <PostizPagePublish />}
        </>
      ) : (
        <PostizHistory />
      )}
    </div>
  );
};

export default PostizPublish;
