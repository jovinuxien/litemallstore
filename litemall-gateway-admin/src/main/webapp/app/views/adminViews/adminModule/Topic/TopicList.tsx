import {
  ITopic,
  useBatchDeleteTopicsMutation,
  useDeleteTopicMutation,
  useListTopicsQuery,
} from 'app/shared/reducers/private/services/adminParityApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Admin topic (专题) catalogue — title/subtitle filter, sort and paging, plus
// create/edit/delete and multi-select batch-delete, through the gateway as an
// authenticated admin (adminParityApi → /srv/private/admin/topic). Cloned from
// BrandList; contract per goods-management docs/handoff-content-endpoints.md §5.

const SORTS = [
  { value: 'add_time', label: 'Date added' },
  { value: 'sort_order', label: 'Sort order' },
  { value: 'title', label: 'Title' },
  { value: 'price', label: 'Price' },
];

const TopicList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [sort, setSort] = React.useState('add_time');
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');
  const [titleInput, setTitleInput] = React.useState('');
  const [subtitleInput, setSubtitleInput] = React.useState('');
  const [filters, setFilters] = React.useState<{ title?: string; subtitle?: string }>({});

  const { data, isLoading, isFetching, isError, error } = useListTopicsQuery({ page, limit, sort, order, ...filters });
  const [deleteTopic, { isLoading: deleting }] = useDeleteTopicMutation();
  const [batchDeleteTopics, { isLoading: batchDeleting }] = useBatchDeleteTopicsMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [selected, setSelected] = React.useState<number[]>([]);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  // Drop selections that fell off the current page (filter/page change).
  React.useEffect(() => {
    setSelected(prev => prev.filter(id => list.some(t => t.id === id)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setFilters({ title: titleInput.trim() || undefined, subtitle: subtitleInput.trim() || undefined });
  };

  const toggle = (id?: number) => {
    if (id == null) return;
    setSelected(prev => (prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]));
  };

  const pageIds = list.map(t => t.id).filter((id): id is number => id != null);
  const allChecked = pageIds.length > 0 && pageIds.every(id => selected.includes(id));
  const toggleAll = () => setSelected(allChecked ? [] : pageIds);

  const onDelete = async (topic: ITopic) => {
    if (!window.confirm(`Delete topic "${topic.title ?? topic.id}"?`)) return;
    setActionError(null);
    const res = await deleteTopic({ id: topic.id as number });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  const onBatchDelete = async () => {
    if (selected.length === 0) return;
    if (!window.confirm(`Delete ${selected.length} selected topic${selected.length === 1 ? '' : 's'}?`)) return;
    setActionError(null);
    const res = await batchDeleteTopics({ ids: selected });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
    else setSelected([]);
  };

  const busy = deleting || batchDeleting;

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input
          className='form-control filter-item'
          style={{ width: 180 }}
          placeholder='Title'
          value={titleInput}
          onChange={e => setTitleInput(e.target.value)}
        />
        <input
          className='form-control filter-item'
          style={{ width: 180 }}
          placeholder='Subtitle'
          value={subtitleInput}
          onChange={e => setSubtitleInput(e.target.value)}
        />
        <select className='form-select filter-item' style={{ width: 150 }} value={sort} onChange={e => setSort(e.target.value)} aria-label='Sort field'>
          {SORTS.map(o => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 110 }}
          value={order}
          onChange={e => setOrder(e.target.value as 'asc' | 'desc')}
          aria-label='Sort direction'
        >
          <option value='desc'>Desc</option>
          <option value='asc'>Asc</option>
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
        <button className='btn btn-primary filter-item' type='submit'>
          Search
        </button>
        <Link className='btn btn-success filter-item' to='/admin/promotion/topic/create'>
          + New topic
        </Link>
        <button className='btn btn-outline-danger filter-item' type='button' disabled={selected.length === 0 || busy} onClick={onBatchDelete}>
          Delete selected{selected.length > 0 ? ` (${selected.length})` : ''}
        </button>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load topics{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th style={{ width: 32 }}>
              <input type='checkbox' className='form-check-input' checked={allChecked} onChange={toggleAll} aria-label='Select all on page' />
            </th>
            <th>Image</th>
            <th>Title</th>
            <th>Subtitle</th>
            <th className='text-end'>Price</th>
            <th className='text-end'>Sort</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={7} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={7} className='text-center text-muted py-5'>
                No topics found.
              </td>
            </tr>
          ) : (
            list.map(topic => (
              <tr key={topic.id}>
                <td>
                  <input
                    type='checkbox'
                    className='form-check-input'
                    checked={topic.id != null && selected.includes(topic.id)}
                    onChange={() => toggle(topic.id)}
                    aria-label={`Select topic ${topic.id}`}
                  />
                </td>
                <td style={{ width: 56 }}>
                  {topic.picUrl ? <img src={topic.picUrl} alt={topic.title ?? ''} className='cell-thumb' /> : <div className='cell-thumb' style={{ background: '#eee' }} />}
                </td>
                <td>
                  <Link to={`/admin/promotion/topic/${topic.id}`}>{topic.title || `#${topic.id}`}</Link>
                </td>
                <td className='text-muted small'>{topic.subtitle}</td>
                <td className='text-end'>¥{Number(topic.price ?? 0).toFixed(2)}</td>
                <td className='text-end'>{topic.sortOrder ?? '—'}</td>
                <td className='text-end'>
                  <Link to={`/admin/promotion/topic/${topic.id}`} className='btn btn-sm btn-outline-primary me-1'>
                    Edit
                  </Link>
                  <button className='btn btn-sm btn-outline-danger' disabled={busy} onClick={() => onDelete(topic)}>
                    Delete
                  </button>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default TopicList;
