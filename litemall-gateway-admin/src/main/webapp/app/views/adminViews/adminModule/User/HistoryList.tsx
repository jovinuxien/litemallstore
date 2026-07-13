import { useListHistoryQuery } from 'app/shared/reducers/private/services/adminParityApi';
import { PAGE_SIZES, Pagination, Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// Admin view of customer search history (litemall_search_history) —
// userId/keyword filters + paging, through the gateway as an authenticated
// admin (adminParityApi → /srv/private/admin/history/list). Cloned from
// FootprintList; contract per goods-management docs/handoff-content-endpoints.md §6.

const fmtTime = (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '—');

const HistoryList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [userIdInput, setUserIdInput] = React.useState('');
  const [keywordInput, setKeywordInput] = React.useState('');
  const [filters, setFilters] = React.useState<{ userId?: string; keyword?: string }>({});

  const { data, isLoading, isFetching, isError, error } = useListHistoryQuery({ page, limit, ...filters });

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setFilters({ userId: userIdInput.trim() || undefined, keyword: keywordInput.trim() || undefined });
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
          style={{ width: 180 }}
          placeholder='Keyword'
          value={keywordInput}
          onChange={e => setKeywordInput(e.target.value)}
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

      {isError && <div className='alert alert-danger'>Failed to load search history{errStatus ? ` (${errStatus})` : ''}.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>ID</th>
            <th>User</th>
            <th>Keyword</th>
            <th>Searched at</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={4} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={4} className='text-center text-muted py-5'>
                No search history found.
              </td>
            </tr>
          ) : (
            list.map(h => (
              <tr key={h.id}>
                <td>{h.id}</td>
                <td>#{h.userId}</td>
                <td>{h.keyword}</td>
                <td className='text-muted small'>{fmtTime(h.addTime)}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default HistoryList;
