import {
  IFreightTemplate,
  useDeleteFreightTemplateMutation,
  useListFreightTemplatesQuery,
  useSetDefaultFreightTemplateMutation,
} from 'app/shared/reducers/private/services/adminFreightApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Admin freight-template list (adminFreightApi → /srv/private/admin/freight,
// served by litemall-order through the gateway). Mirrors the BrandList look:
// paged .el-table with edit / set-default / delete actions. Delete is refused
// server-side (errno != 0 / HTTP 422) while a live goods still references the
// template — the errmsg is surfaced inline instead of navigating away.

export const APPOINT_LABELS: Record<number, string> = {
  // TODO(freight-contract): confirm enum meaning against the committed spec.
  0: 'Charge by region',
  1: 'With free-shipping rules',
  2: 'Always free',
};

const FreightTemplateList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);

  const { data, isLoading, isFetching, isError, error } = useListFreightTemplatesQuery({ page, limit });
  const [deleteTemplate, { isLoading: deleting }] = useDeleteFreightTemplateMutation();
  const [setDefault, { isLoading: settingDefault }] = useSetDefaultFreightTemplateMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onDelete = async (tpl: IFreightTemplate) => {
    if (!window.confirm(`Delete freight template "${tpl.name || `#${tpl.id}`}"?`)) return;
    setActionError(null);
    const res = await deleteTemplate({ id: tpl.id as number });
    if ('error' in res) {
      // HTTP-level failure (e.g. a real 422 status): dig the errmsg out of the body if present.
      const errData = (res.error as { data?: { errmsg?: string } })?.data;
      setActionError(errData?.errmsg || 'Delete failed — the template may still be referenced by a goods.');
      return;
    }
    const msg = errnoMessage(res.data);
    if (msg) setActionError(msg);
  };

  const onSetDefault = async (tpl: IFreightTemplate) => {
    setActionError(null);
    const res = await setDefault({ id: tpl.id as number });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
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
        <Link className='btn btn-success filter-item' to='/admin/mall/freight/create'>
          + New template
        </Link>
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load freight templates{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th style={{ width: 70 }}>ID</th>
            <th>Name</th>
            <th>Mode</th>
            <th>Default</th>
            <th className='text-end'>Sort</th>
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
                No freight templates yet.
              </td>
            </tr>
          ) : (
            list.map(tpl => (
              <tr key={tpl.id}>
                <td>#{tpl.id}</td>
                <td>
                  <Link to={`/admin/mall/freight/${tpl.id}`}>{tpl.name || `#${tpl.id}`}</Link>
                </td>
                <td>{APPOINT_LABELS[tpl.appoint] ?? `Mode ${tpl.appoint}`}</td>
                <td>{tpl.isDefault ? <Tag tag='success'>Default</Tag> : <span className='text-muted'>—</span>}</td>
                <td className='text-end'>{tpl.sortOrder ?? '—'}</td>
                <td className='text-end'>
                  <Link to={`/admin/mall/freight/${tpl.id}`} className='btn btn-sm btn-outline-primary me-1'>
                    Edit
                  </Link>
                  <button
                    className='btn btn-sm btn-outline-secondary me-1'
                    disabled={settingDefault || Boolean(tpl.isDefault)}
                    onClick={() => onSetDefault(tpl)}
                  >
                    Set default
                  </button>
                  <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(tpl)}>
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

export default FreightTemplateList;
