import { useListCollectsQuery } from 'app/shared/reducers/private/services/adminEngagementApi';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Admin view of customer favourites (litemall_collect) — userId/valueId
// filters + paging, through the gateway as an authenticated admin
// (adminEngagementApi → /srv/private/admin/collect/list). Mirrors AddressList.

const fmtTime = (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '—');

const CollectList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [userIdInput, setUserIdInput] = React.useState('');
  const [valueIdInput, setValueIdInput] = React.useState('');
  const [filters, setFilters] = React.useState<{ userId?: string; valueId?: string }>({});

  const { data, isLoading, isFetching, isError, error } = useListCollectsQuery({ page, limit, ...filters });

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setFilters({ userId: userIdInput.trim() || undefined, valueId: valueIdInput.trim() || undefined });
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input
          className='form-control filter-item'
          style={{ width: 140 }}
          placeholder='User ID'
          value={userIdInput}
          onChange={e => setUserIdInput(e.target.value.replace(/[^\d]/g, ''))}
        />
        <input
          className='form-control filter-item'
          style={{ width: 140 }}
          placeholder='Goods ID'
          value={valueIdInput}
          onChange={e => setValueIdInput(e.target.value.replace(/[^\d]/g, ''))}
        />
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
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load collections{errStatus ? ` (${errStatus})` : ''}.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>ID</th>
            <th>User</th>
            <th>Target</th>
            <th>Kind</th>
            <th>Added</th>
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
                No collections found.
              </td>
            </tr>
          ) : (
            list.map(c => (
              <tr key={c.id}>
                <td>{c.id}</td>
                <td>#{c.userId}</td>
                <td>{c.type === 0 ? <Link to={`/admin/goods/${c.valueId}`}>goods #{c.valueId}</Link> : `topic #${c.valueId}`}</td>
                <td>{c.type === 0 ? <Tag tag='primary'>goods</Tag> : <Tag tag='info'>topic</Tag>}</td>
                <td className='text-muted small'>{fmtTime(c.addTime)}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default CollectList;
