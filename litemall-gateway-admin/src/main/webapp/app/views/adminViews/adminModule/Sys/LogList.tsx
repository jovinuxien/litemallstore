import { useListLogsQuery } from 'app/shared/reducers/private/services/adminSysApi';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// Read-only operation-log viewer, authenticated admin →
// /srv/private/admin/log. Rows are written by AdminAuditLogFilter on every
// admin mutation through the gateway.

const LogList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');
  const [nameInput, setNameInput] = React.useState('');
  const [name, setName] = React.useState('');

  const { data, isLoading, isFetching, isError, error } = useListLogsQuery({ page, limit, sort: 'add_time', order, name });

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setName(nameInput.trim());
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input className='form-control filter-item' style={{ width: 200 }} placeholder='Admin name' value={nameInput} onChange={e => setNameInput(e.target.value)} />
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
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load logs{errStatus ? ` (${errStatus})` : ''}.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Admin</th>
            <th>IP</th>
            <th>Action</th>
            <th>Result</th>
            <th>Status</th>
            <th>When</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={6} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={6} className='text-center text-muted py-5'>
                No log entries.
              </td>
            </tr>
          ) : (
            list.map(l => (
              <tr key={l.id}>
                <td>{l.admin || '—'}</td>
                <td className='text-muted small'>{l.ip}</td>
                <td className='small'>{l.action}</td>
                <td className='text-muted small'>{l.result}</td>
                <td>{l.status ? <Tag tag='success'>ok</Tag> : <Tag tag='danger'>fail</Tag>}</td>
                <td className='text-muted small'>{l.addTime?.replace('T', ' ').slice(0, 19)}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default LogList;
