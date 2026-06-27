import { IKeyword } from 'app/shared/model/admin/catalog.model';
import { useDeleteKeywordMutation, useListKeywordsQuery } from 'app/shared/reducers/private/services/adminCatalogApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Admin search-keyword list (hot/default flags) with create/edit/delete,
// authenticated admin → /srv/private/admin/keyword.

const KeywordList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');
  const [kwInput, setKwInput] = React.useState('');
  const [keyword, setKeyword] = React.useState('');

  const { data, isLoading, isFetching, isError, error } = useListKeywordsQuery({ page, limit, sort: 'add_time', order, keyword });
  const [deleteKeyword, { isLoading: deleting }] = useDeleteKeywordMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setKeyword(kwInput.trim());
  };

  const onDelete = async (kw: IKeyword) => {
    if (!window.confirm(`Delete keyword "${kw.keyword ?? kw.id}"?`)) return;
    setActionError(null);
    const res = await deleteKeyword({ id: kw.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input className='form-control filter-item' style={{ width: 200 }} placeholder='Keyword' value={kwInput} onChange={e => setKwInput(e.target.value)} />
        <select className='form-select filter-item' style={{ width: 120 }} value={order} onChange={e => setOrder(e.target.value as 'asc' | 'desc')} aria-label='Sort direction'>
          <option value='desc'>Newest</option>
          <option value='asc'>Oldest</option>
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
        <Link className='btn btn-success filter-item' to='/admin/mall/keyword/create'>
          + New keyword
        </Link>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load keywords{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Keyword</th>
            <th>URL</th>
            <th>Flags</th>
            <th className='text-end'>Sort</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={5} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={5} className='text-center text-muted py-5'>
                No keywords found.
              </td>
            </tr>
          ) : (
            list.map(kw => (
              <tr key={kw.id}>
                <td>
                  <Link to={`/admin/mall/keyword/${kw.id}`}>{kw.keyword || `#${kw.id}`}</Link>
                </td>
                <td className='text-muted small'>{kw.url}</td>
                <td>
                  {kw.isHot && (
                    <span className='me-1'>
                      <Tag tag='danger'>hot</Tag>
                    </span>
                  )}
                  {kw.isDefault && <Tag tag='primary'>default</Tag>}
                </td>
                <td className='text-end'>{kw.sortOrder ?? '—'}</td>
                <td className='text-end'>
                  <Link to={`/admin/mall/keyword/${kw.id}`} className='btn btn-sm btn-outline-primary me-1'>
                    Edit
                  </Link>
                  <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(kw)}>
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

export default KeywordList;
