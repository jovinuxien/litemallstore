import { useListPinksQuery } from 'app/shared/reducers/private/services/adminPromotionApi';
import { Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Group-buy activity monitor: running/finished groups (pinks) across all
// campaigns, optionally filtered by status, authenticated admin →
// promotion-service /srv/private/admin/promotion/combination/pinks.

const STATUS_TAG: Record<number, { tag: 'warning' | 'success' | 'info'; text: string }> = {
  0: { tag: 'warning', text: 'pending' },
  1: { tag: 'success', text: 'succeeded' },
  2: { tag: 'info', text: 'failed' },
};

const GrouponActivityList: React.FC = () => {
  const [status, setStatus] = React.useState<number | undefined>(undefined);

  const { data, isLoading, isFetching, isError, error } = useListPinksQuery({ status });

  const list = data?.list ?? [];
  const errStatus = (error as { status?: number | string })?.status;

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <Link className='btn btn-outline-secondary filter-item' to='/admin/promotion/groupon-rule'>
          ‹ Back to campaigns
        </Link>
        <select
          className='form-select filter-item'
          style={{ width: 160 }}
          value={status ?? ''}
          onChange={e => setStatus(e.target.value === '' ? undefined : Number(e.target.value))}
          aria-label='Status filter'
        >
          <option value=''>All statuses</option>
          <option value={0}>Pending</option>
          <option value={1}>Succeeded</option>
          <option value={2}>Failed</option>
        </select>
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load group-buy activity{errStatus ? ` (${errStatus})` : ''}.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th className='text-end'>Group</th>
            <th className='text-end'>Campaign</th>
            <th className='text-end'>Leader (user)</th>
            <th className='text-end'>Order</th>
            <th className='text-end'>Participants</th>
            <th>Expires</th>
            <th>Status</th>
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
                No group-buy activity yet.
              </td>
            </tr>
          ) : (
            list.map((p, i) => {
              const st = STATUS_TAG[p.status ?? 0] ?? STATUS_TAG[0];
              return (
                <tr key={p.pinkId ?? i}>
                  <td className='text-end'>{p.pinkId}</td>
                  <td className='text-end'>#{p.combinationId}</td>
                  <td className='text-end'>{p.userId}</td>
                  <td className='text-end'>{p.orderId ?? '—'}</td>
                  <td className='text-end'>
                    {p.memberCount ?? '?'} / {p.requiredMembers ?? '?'}
                  </td>
                  <td className='text-muted small'>{p.expireTime?.replace('T', ' ').slice(0, 16) ?? '—'}</td>
                  <td>
                    <Tag tag={st.tag}>{st.text}</Tag>
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

export default GrouponActivityList;
