import { ICombination } from 'app/shared/model/admin/promotion-system.model';
import {
  promotionOpMessage,
  useActivateCombinationMutation,
  useDeleteCombinationMutation,
  useExpireCombinationMutation,
  useListCombinationsQuery,
} from 'app/shared/reducers/private/services/adminPromotionApi';
import { Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Group-buy (combination) campaign rules with create/edit/delete plus the
// explicit activate/expire lifecycle, authenticated admin → promotion-service
// /srv/private/admin/promotion/combination. Campaigns are created in DRAFT and
// only become customer-visible after activation. The list endpoint is
// unpaginated (returns all campaigns).

const STATUS_TAG: Record<number, { tag: 'info' | 'success' | 'warning'; text: string }> = {
  0: { tag: 'info', text: 'draft' },
  1: { tag: 'success', text: 'active' },
  2: { tag: 'warning', text: 'expired' },
  3: { tag: 'warning', text: 'offline' },
};

const GrouponRuleList: React.FC = () => {
  const { data, isLoading, isFetching, isError, error } = useListCombinationsQuery();
  const [deleteCombination, { isLoading: deleting }] = useDeleteCombinationMutation();
  const [activateCombination, { isLoading: activating }] = useActivateCombinationMutation();
  const [expireCombination, { isLoading: expiring }] = useExpireCombinationMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const errStatus = (error as { status?: number | string })?.status;
  const busy = deleting || activating || expiring;

  const run = async (action: Promise<unknown>) => {
    setActionError(null);
    setActionError(promotionOpMessage(await action));
  };

  const onDelete = (c: ICombination) => {
    if (!window.confirm(`Delete group-buy campaign "${c.title ?? c.id}"?`)) return;
    void run(deleteCombination(c));
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <Link className='btn btn-success filter-item' to='/admin/promotion/groupon-rule/create'>
          + New group-buy campaign
        </Link>
        <Link className='btn btn-outline-secondary filter-item' to='/admin/promotion/groupon-activity'>
          View activity
        </Link>
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load group-buy campaigns{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Image</th>
            <th>Campaign</th>
            <th className='text-end'>Group price</th>
            <th className='text-end'>Original</th>
            <th className='text-end'>Members</th>
            <th>Window</th>
            <th>Status</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={8} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={8} className='text-center text-muted py-5'>
                No group-buy campaigns found.
              </td>
            </tr>
          ) : (
            list.map(c => {
              const st = STATUS_TAG[c.status ?? 0] ?? STATUS_TAG[0];
              return (
                <tr key={c.id}>
                  <td>{c.picUrl ? <img src={c.picUrl} alt={c.title} style={{ height: 40, width: 40, objectFit: 'cover' }} /> : <span className='text-muted'>—</span>}</td>
                  <td>
                    <Link to={`/admin/promotion/groupon-rule/${c.id}`}>{c.title || `#${c.id}`}</Link>
                    <div className='text-muted small'>goods #{c.goodsId}</div>
                  </td>
                  <td className='text-end'>{c.combinationPrice ?? '—'}</td>
                  <td className='text-end'>{c.originalPrice ?? '—'}</td>
                  <td className='text-end'>{c.requiredMembers ?? '—'}</td>
                  <td className='text-muted small'>
                    {c.startTime?.replace('T', ' ').slice(0, 16)} – {c.endTime?.replace('T', ' ').slice(0, 16)}
                  </td>
                  <td>
                    <Tag tag={st.tag}>{st.text}</Tag>
                  </td>
                  <td className='text-end'>
                    {c.status !== 1 && (
                      <button className='btn btn-sm btn-outline-success me-1' disabled={busy} onClick={() => void run(activateCombination(c.id as number))}>
                        Activate
                      </button>
                    )}
                    {c.status === 1 && (
                      <button className='btn btn-sm btn-outline-warning me-1' disabled={busy} onClick={() => void run(expireCombination(c.id as number))}>
                        Expire
                      </button>
                    )}
                    <Link to={`/admin/promotion/groupon-rule/${c.id}`} className='btn btn-sm btn-outline-primary me-1'>
                      Edit
                    </Link>
                    <button className='btn btn-sm btn-outline-danger' disabled={busy} onClick={() => onDelete(c)}>
                      Delete
                    </button>
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
};

export default GrouponRuleList;
