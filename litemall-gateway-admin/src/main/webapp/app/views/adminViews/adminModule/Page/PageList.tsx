import {
  IPageSummary,
  useActivatePageMutation,
  useClonePageMutation,
  useDeactivatePageMutation,
  useDeletePageMutation,
  useListPagesQuery,
} from 'app/shared/reducers/private/services/adminContentApi';
import { fromServerDateTime } from 'app/shared/util/server-datetime';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { categoryLabel, categoryTag, clonedPageId } from './pageFormat';
import * as React from 'react';
import { Link, useNavigate } from 'react-router-dom';

// DIY page list — position (home/custom) and status (draft/active) badges,
// edit / activate / deactivate / delete. Activating a HOME-position page is a
// transactional home swap on the server: the current live home is demoted in
// the same transaction, so the confirm dialog spells that out. A lost
// concurrent-activation race and a delete of the active home both come back
// as errno 641 — the errmsg is surfaced inline verbatim.
// Wave 20 adds category (general/coupon/groupon) + template filters and the
// clone endpoint: "Use template" on a template row (and "Duplicate" on a
// regular row) both POST /page/{id}/clone — the copy is always a DRAFT named
// "Copy of …" with position custom and the source category inherited.
// Contract: litemall-goods-management/docs/spec-page-palette-v1.md §5–§6.

// Tolerates the raw array shape too — a data-shape surprise must never throw
// in render (a crash here trips the app-level ErrorBoundary for the session).
const fmtTime = (t?: unknown) => {
  const s = fromServerDateTime(t);
  return s ? s.replace('T', ' ').slice(0, 16) : '—';
};

const activateConfirmText = (p: IPageSummary): string =>
  p.position === 'home'
    ? `Activate "${p.name}"?\n\nThis page is in the HOME position. Activating it IMMEDIATELY replaces the current live home page for ALL customers (the previous active home page is demoted to draft in the same transaction). Continue?`
    : `Activate "${p.name}"?\n\nThe page becomes reachable by customers at its page URL.`;

const PageList: React.FC = () => {
  const navigate = useNavigate();
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [position, setPosition] = React.useState('');
  const [status, setStatus] = React.useState('');
  const [category, setCategory] = React.useState('');
  const [templatesOnly, setTemplatesOnly] = React.useState(false);

  const { data, isLoading, isFetching, isError, error } = useListPagesQuery({
    page,
    limit,
    position: position as '' | 'home' | 'custom',
    status: status as '' | 'draft' | 'active',
    category: category as '' | 'general' | 'coupon' | 'groupon',
    template: templatesOnly ? 1 : undefined,
  });
  const [activatePage, { isLoading: activating }] = useActivatePageMutation();
  const [deactivatePage, { isLoading: deactivating }] = useDeactivatePageMutation();
  const [deletePage, { isLoading: deleting }] = useDeletePageMutation();
  const [clonePage, { isLoading: cloning }] = useClonePageMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = limit > 0 ? Math.ceil(total / limit) : 0;
  const errStatus = (error as { status?: number | string })?.status;
  const busy = activating || deactivating || deleting || cloning;

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

  // Clone = "Use template" (template rows, jumps into the editor on the copy)
  // and "Duplicate" (regular rows, the copy appears in the refreshed list).
  const onClone = async (p: IPageSummary, openEditor: boolean) => {
    setActionError(null);
    const res = await clonePage({ id: p.id });
    if (!('data' in res)) {
      setActionError('Request failed.');
      return;
    }
    const msg = errnoMessage(res.data);
    if (msg) {
      setActionError(msg);
      return;
    }
    const newId = clonedPageId(res.data);
    if (openEditor && newId != null) navigate(`/admin/mall/page/${newId}`);
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
          style={{ width: 160 }}
          value={category}
          onChange={e => {
            setPage(1);
            setCategory(e.target.value);
          }}
          aria-label='Category'
        >
          <option value=''>All categories</option>
          <option value='general'>General</option>
          <option value='coupon'>Coupon</option>
          <option value='groupon'>Groupon</option>
        </select>
        <div className='form-check filter-item' style={{ paddingTop: 6 }}>
          <input
            id='page-templates-only'
            className='form-check-input'
            type='checkbox'
            checked={templatesOnly}
            onChange={e => {
              setPage(1);
              setTemplatesOnly(e.target.checked);
            }}
          />
          <label className='form-check-label' htmlFor='page-templates-only'>
            Templates
          </label>
        </div>
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
        {!templatesOnly && (
          <button
            type='button'
            className='btn btn-outline-success filter-item'
            title='Show the seeded template pages — "Use template" clones one into a fresh draft'
            onClick={() => {
              setPage(1);
              setTemplatesOnly(true);
            }}
          >
            New from template…
          </button>
        )}
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load pages{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Name</th>
            <th>Category</th>
            <th>Position</th>
            <th>Status</th>
            <th>Updated</th>
            <th className='text-end'>Actions</th>
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
                {templatesOnly ? 'No template pages match the current filters.' : 'No pages yet. Create one to replace the legacy home page.'}
              </td>
            </tr>
          ) : (
            list.map(p => (
              <tr key={p.id}>
                <td>
                  <Link to={`/admin/mall/page/${p.id}`}>{p.name || `#${p.id}`}</Link>
                  {p.isTemplate && (
                    <span className='ms-2'>
                      <Tag tag='primary'>Template</Tag>
                    </span>
                  )}
                </td>
                <td>
                  <Tag tag={categoryTag(p.category)}>{categoryLabel(p.category)}</Tag>
                </td>
                <td>{p.position === 'home' ? <Tag tag='primary'>home</Tag> : <Tag tag='info'>custom</Tag>}</td>
                <td>{p.status === 'active' ? <Tag tag='success'>active</Tag> : <Tag tag='warning'>draft</Tag>}</td>
                <td className='text-muted small'>{fmtTime(p.updateTime)}</td>
                <td className='text-end'>
                  <Link to={`/admin/mall/page/${p.id}`} className='btn btn-sm btn-outline-primary me-1'>
                    Edit
                  </Link>
                  {p.isTemplate ? (
                    <button
                      className='btn btn-sm btn-success me-1'
                      disabled={busy}
                      title='Clone this template into a fresh draft and open it in the editor'
                      onClick={() => onClone(p, true)}
                    >
                      Use template
                    </button>
                  ) : (
                    <button
                      className='btn btn-sm btn-outline-secondary me-1'
                      disabled={busy}
                      title='Clone this page into a fresh draft ("Copy of …")'
                      onClick={() => onClone(p, false)}
                    >
                      Duplicate
                    </button>
                  )}
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
