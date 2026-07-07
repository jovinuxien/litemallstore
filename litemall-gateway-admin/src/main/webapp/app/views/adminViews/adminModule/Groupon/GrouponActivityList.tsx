import { useListGrouponRecordsQuery } from 'app/shared/reducers/private/services/adminPromotionApi';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Read-only list of running/finished groupon activities (litemall_groupon head
// records with joined participants), authenticated admin →
// /srv/private/admin/groupon/listRecord.

const STATUS_TAG: Record<number, { tag: 'warning' | 'success' | 'info'; text: string }> = {
  0: { tag: 'warning', text: 'in progress' },
  1: { tag: 'success', text: 'succeeded' },
  2: { tag: 'info', text: 'failed' },
};

const GrouponActivityList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);

  const { data, isLoading, isFetching, isError, error } = useListGrouponRecordsQuery({ page, limit, sort: 'add_time', order: 'desc' });

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <Link className='btn btn-outline-secondary filter-item' to='/admin/promotion/groupon-rule'>
          ‹ Back to rules
        </Link>
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
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load groupon activity{errStatus ? ` (${errStatus})` : ''}.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Goods</th>
            <th className='text-end'>Group ID</th>
            <th className='text-end'>Creator</th>
            <th className='text-end'>Participants</th>
            <th>Status</th>
            <th>Started</th>
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
                No groupon activity yet.
              </td>
            </tr>
          ) : (
            list.map((rec, i) => {
              const g = rec.groupon ?? {};
              const st = STATUS_TAG[g.status ?? 0] ?? STATUS_TAG[0];
              const participants = (rec.subGroupons?.length ?? 0) + 1;
              return (
                <tr key={g.id ?? i}>
                  <td>
                    {rec.goods?.picUrl && <img src={rec.goods.picUrl} alt='' style={{ height: 32, width: 32, objectFit: 'cover' }} className='me-2' />}
                    {rec.goods?.name || rec.rules?.goodsName || '—'}
                  </td>
                  <td className='text-end'>{g.id}</td>
                  <td className='text-end'>{g.creatorUserId ?? g.userId}</td>
                  <td className='text-end'>
                    {participants} / {rec.rules?.discountMember ?? '?'}
                  </td>
                  <td>
                    <Tag tag={st.tag}>{st.text}</Tag>
                  </td>
                  <td className='text-muted small'>{g.addTime?.replace('T', ' ').slice(0, 16)}</td>
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

export default GrouponActivityList;
