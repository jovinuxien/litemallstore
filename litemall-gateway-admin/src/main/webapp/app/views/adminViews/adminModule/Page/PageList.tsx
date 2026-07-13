import {
  IPageSummary,
  useActivatePageMutation,
  useDeactivatePageMutation,
  useDeletePageMutation,
  useListPagesQuery,
} from 'app/shared/reducers/private/services/adminContentApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// DIY page list — position (home/custom) and status (draft/active) badges,
// edit / activate / deactivate / delete. Activating a HOME-position page is a
// transactional home swap on the server: the current live home is demoted in
// the same transaction, so the confirm dialog spells that out. A lost
// concurrent-activation race and a delete of the active home both come back
// as errno 641 — the errmsg is surfaced inline verbatim.
// Contract: litemall-goods-management/docs/spec-page-palette-v1.md §5–§6.

const fmtTime = (t?: string) => (t ? t.replace('T', ' ').slice(0, 16) : '—');

const activateConfirmText = (p: IPageSummary): string =>
  p.position === 'home'
    ? `Activate "${p.name}"?\n\nThis page is in the HOME position. Activating it IMMEDIATELY replaces the current live home page for ALL customers (the previous active home page is demoted to draft in the same transaction). Continue?`
    : `Activate "${p.name}"?\n\nThe page becomes reachable by customers at its page URL.`;

const PageList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [position, setPosition] = React.useState('');
  const [status, setStatus] = React.useState('');

  const { data, isLoading, isFetching, isError, error } = useListPagesQuery({
    page,
    limit,
    position: position as '' | 'home' | 'custom',
    status: status as '' | 'draft' | 'active',
  });
  const [activatePage, { isLoading: activating }] = useActivatePageMutation();
  const [deactivatePage, { isLoading: deactivating }] = useDeactivatePageMutation();
  const [deletePage, { isLoading: deleting }] = useDeletePageMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = limit > 0 ? Math.ceil(total / limit) : 0;
  const errStatus = (error as { status?: number | string })?.status;
  const busy = activating || deactivating || deleting;

  // 641 = lost a concurrent home-activation race — surface errmsg verbatim.
  const onActivate = async (p: IPageSummary) => {
    if (!window.confirm(activateConfirmText(p))) return;
    setActionError(null);
    const res = await activatePage({ id: p.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  const onDeactivate = async (p: IPageSummary) => {
    const warn =
      p.position === 'home'
        ? `Deactivate "${p.name}"?\n\nCustomers will fall back to the built-in legacy home page until another home page is activated.`
        : `Deactivate "${p.name}"?`;
    if (!window.confirm(warn)) return;
    setActionError(null);
    const res = await deactivatePage({ id: p.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  // Deleting the ACTIVE home is refused server-side (errno 641) — inline errmsg.
  const onDelete = async (p: IPageSummary) => {
    if (!window.confirm(`Delete page "${p.name}"?`)) return;
    setActionError(null);
    const res = await deletePage({ id: p.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={e => e.preventDefault()}>
        <select
          className='form-select filter-item'
          style={{ width: 160 }}
          value={position}
          onChange={e => {
            setPage(1);
            setPosition(e.target.value);
          }}
          aria-label='Position'
        >
          <option value=''>All positions</option>
          <option value='home'>Home</option>
          <option value='custom'>Custom</option>
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
          <option value='draft'>Draft</option>
          <option value='active'>Active</option>
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
        <Link className='btn btn-success filter-item' to='/admin/mall/page/create'>
          + New page
        </Link>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load pages{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Name</th>
            <th>Position</th>
            <th>Status</th>
            <th>Updated</th>
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
                No pages yet. Create one to replace the legacy home page.
              </td>
            </tr>
          ) : (
            list.map(p => (
              <tr key={p.id}>
                <td>
                  <Link to={`/admin/mall/page/${p.id}`}>{p.name || `#${p.id}`}</Link>
                </td>
                <td>{p.position === 'home' ? <Tag tag='primary'>home</Tag> : <Tag tag='info'>custom</Tag>}</td>
                <td>{p.status === 'active' ? <Tag tag='success'>active</Tag> : <Tag tag='warning'>draft</Tag>}</td>
                <td className='text-muted small'>{fmtTime(p.updateTime)}</td>
                <td className='text-end'>
                  <Link to={`/admin/mall/page/${p.id}`} className='btn btn-sm btn-outline-primary me-1'>
                    Edit
                  </Link>
                  {p.status === 'draft' ? (
                    <button className='btn btn-sm btn-outline-success me-1' disabled={busy} onClick={() => onActivate(p)}>
                      Activate
                    </button>
                  ) : (
                    <button className='btn btn-sm btn-outline-warning me-1' disabled={busy} onClick={() => onDeactivate(p)}>
                      Deactivate
                    </button>
                  )}
                  <button className='btn btn-sm btn-outline-danger' disabled={busy} onClick={() => onDelete(p)}>
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

export default PageList;
