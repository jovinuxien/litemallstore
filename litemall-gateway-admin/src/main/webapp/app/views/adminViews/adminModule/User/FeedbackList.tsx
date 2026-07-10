import { useListFeedbackQuery } from 'app/shared/reducers/private/services/adminEngagementApi';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// Admin view of customer feedback (litemall_feedback) — userId/username
// filters + paging, through the gateway as an authenticated admin
// (adminEngagementApi → /srv/private/admin/feedback/list).

const fmtTime = (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '—');

const FeedbackList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [userIdInput, setUserIdInput] = React.useState('');
  const [usernameInput, setUsernameInput] = React.useState('');
  const [filters, setFilters] = React.useState<{ userId?: string; username?: string }>({});

  const { data, isLoading, isFetching, isError, error } = useListFeedbackQuery({ page, limit, ...filters });

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setFilters({ userId: userIdInput.trim() || undefined, username: usernameInput.trim() || undefined });
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
          style={{ width: 200 }}
          placeholder='Username'
          value={usernameInput}
          onChange={e => setUsernameInput(e.target.value)}
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

      {isError && <div className='alert alert-danger'>Failed to load feedback{errStatus ? ` (${errStatus})` : ''}.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>ID</th>
            <th>User</th>
            <th>Mobile</th>
            <th>Type</th>
            <th style={{ maxWidth: 420 }}>Content</th>
            <th>Pictures</th>
            <th>Submitted</th>
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
                No feedback found.
              </td>
            </tr>
          ) : (
            list.map(fb => (
              <tr key={fb.id}>
                <td>{fb.id}</td>
                <td>
                  {fb.username || '—'} <span className='text-muted small'>#{fb.userId}</span>
                </td>
                <td>{fb.mobile || '—'}</td>
                <td>{fb.feedType ? <Tag tag='info'>{fb.feedType}</Tag> : '—'}</td>
                <td style={{ maxWidth: 420, whiteSpace: 'pre-wrap' }}>{fb.content || '—'}</td>
                <td>
                  {fb.hasPicture && fb.picUrls?.length
                    ? fb.picUrls.map((u, i) => <img key={i} src={u} alt='' style={{ height: 32, width: 32, objectFit: 'cover' }} className='me-1' />)
                    : '—'}
                </td>
                <td className='text-muted small'>{fmtTime(fb.addTime)}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default FeedbackList;
