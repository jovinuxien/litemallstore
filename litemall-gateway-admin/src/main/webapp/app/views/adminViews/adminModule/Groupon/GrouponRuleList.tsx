import { IGrouponRule } from 'app/shared/model/admin/promotion-system.model';
import { useDeleteGrouponRuleMutation, useListGrouponRulesQuery } from 'app/shared/reducers/private/services/adminPromotionApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Groupon (group-buy) rules with create/edit/delete,
// authenticated admin → /srv/private/admin/groupon.

const STATUS_TAG: Record<number, { tag: 'success' | 'info' | 'warning'; text: string }> = {
  0: { tag: 'success', text: 'active' },
  1: { tag: 'info', text: 'expired' },
  2: { tag: 'warning', text: 'offline' },
};

const GrouponRuleList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');

  const { data, isLoading, isFetching, isError, error } = useListGrouponRulesQuery({ page, limit, sort: 'add_time', order });
  const [deleteRule, { isLoading: deleting }] = useDeleteGrouponRuleMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onDelete = async (r: IGrouponRule) => {
    if (!window.confirm(`Delete groupon rule for "${r.goodsName ?? r.id}"?`)) return;
    setActionError(null);
    const res = await deleteRule({ id: r.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
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
        <Link className='btn btn-success filter-item' to='/admin/promotion/groupon-rule/create'>
          + New groupon rule
        </Link>
        <Link className='btn btn-outline-secondary filter-item' to='/admin/promotion/groupon-activity'>
          View activity
        </Link>
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load groupon rules{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Image</th>
            <th>Goods</th>
            <th className='text-end'>Discount</th>
            <th className='text-end'>Member size</th>
            <th>Expires</th>
            <th>Status</th>
            <th className='text-end'>Actions</th>
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
                No groupon rules found.
              </td>
            </tr>
          ) : (
            list.map(r => {
              const st = STATUS_TAG[r.status ?? 0] ?? STATUS_TAG[0];
              return (
                <tr key={r.id}>
                  <td>{r.picUrl ? <img src={r.picUrl} alt={r.goodsName} style={{ height: 40, width: 40, objectFit: 'cover' }} /> : <span className='text-muted'>—</span>}</td>
                  <td>
                    <Link to={`/admin/promotion/groupon-rule/${r.id}`}>{r.goodsName || `#${r.goodsId}`}</Link>
                  </td>
                  <td className='text-end'>{r.discount != null ? `-${r.discount}` : '—'}</td>
                  <td className='text-end'>{r.discountMember ?? '—'}</td>
                  <td className='text-muted small'>{r.expireTime?.replace('T', ' ').slice(0, 16)}</td>
                  <td>
                    <Tag tag={st.tag}>{st.text}</Tag>
                  </td>
                  <td className='text-end'>
                    <Link to={`/admin/promotion/groupon-rule/${r.id}`} className='btn btn-sm btn-outline-primary me-1'>
                      Edit
                    </Link>
                    <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(r)}>
                      Delete
                    </button>
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default GrouponRuleList;
