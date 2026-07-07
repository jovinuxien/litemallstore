import { IAdmin } from 'app/shared/model/admin/promotion-system.model';
import { useDeleteAdminMutation, useListAdminsQuery } from 'app/shared/reducers/private/services/adminSysApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Admin-account management with create/edit/delete,
// authenticated admin → /srv/private/admin/admin.

const AdminAccountList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');
  const [nameInput, setNameInput] = React.useState('');
  const [username, setUsername] = React.useState('');

  const { data, isLoading, isFetching, isError, error } = useListAdminsQuery({ page, limit, sort: 'add_time', order, username });
  const [deleteAdmin, { isLoading: deleting }] = useDeleteAdminMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setUsername(nameInput.trim());
  };

  const onDelete = async (a: IAdmin) => {
    if (!window.confirm(`Delete administrator "${a.username ?? a.id}"?`)) return;
    setActionError(null);
    const res = await deleteAdmin({ id: a.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input className='form-control filter-item' style={{ width: 200 }} placeholder='Username' value={nameInput} onChange={e => setNameInput(e.target.value)} />
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
        <Link className='btn btn-success filter-item' to='/admin/sys/admin/create'>
          + New administrator
        </Link>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load administrators{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Username</th>
            <th>Last login IP</th>
            <th>Last login</th>
            <th className='text-end'>Actions</th>
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
                No administrators found.
              </td>
            </tr>
          ) : (
            list.map(a => (
              <tr key={a.id}>
                <td>
                  <Link to={`/admin/sys/admin/${a.id}`}>{a.username || `#${a.id}`}</Link>
                </td>
                <td className='text-muted small'>{a.lastLoginIp || '—'}</td>
                <td className='text-muted small'>{a.lastLoginTime?.replace('T', ' ').slice(0, 16) || '—'}</td>
                <td className='text-end'>
                  <Link to={`/admin/sys/admin/${a.id}`} className='btn btn-sm btn-outline-primary me-1'>
                    Edit
                  </Link>
                  <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(a)}>
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

export default AdminAccountList;
