import { INotice } from 'app/shared/model/admin/promotion-system.model';
import { useDeleteNoticeMutation, useListNoticesQuery } from 'app/shared/reducers/private/services/adminSysApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Notice management with create/edit/delete,
// authenticated admin → /srv/private/admin/notice.

const NoticeList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');
  const [titleInput, setTitleInput] = React.useState('');
  const [title, setTitle] = React.useState('');

  const { data, isLoading, isFetching, isError, error } = useListNoticesQuery({ page, limit, sort: 'add_time', order, title });
  const [deleteNotice, { isLoading: deleting }] = useDeleteNoticeMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setTitle(titleInput.trim());
  };

  const onDelete = async (n: INotice) => {
    if (!window.confirm(`Delete notice "${n.title ?? n.id}"?`)) return;
    setActionError(null);
    const res = await deleteNotice({ id: n.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input className='form-control filter-item' style={{ width: 200 }} placeholder='Title' value={titleInput} onChange={e => setTitleInput(e.target.value)} />
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
        <Link className='btn btn-success filter-item' to='/admin/sys/notice/create'>
          + New notice
        </Link>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load notices{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Title</th>
            <th>Content</th>
            <th>Created</th>
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
                No notices found.
              </td>
            </tr>
          ) : (
            list.map(n => (
              <tr key={n.id}>
                <td>
                  <Link to={`/admin/sys/notice/${n.id}`}>{n.title || `#${n.id}`}</Link>
                </td>
                <td className='text-muted small' style={{ maxWidth: 360, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {n.content}
                </td>
                <td className='text-muted small'>{n.addTime?.replace('T', ' ').slice(0, 16)}</td>
                <td className='text-end'>
                  <Link to={`/admin/sys/notice/${n.id}`} className='btn btn-sm btn-outline-primary me-1'>
                    Edit
                  </Link>
                  <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(n)}>
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

export default NoticeList;
