import { useGetPostizLogQuery } from 'app/shared/reducers/private/services/postizApi';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { fmtDateTime } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Wave 17: publish history — the litemall_postiz_post ledger via GET /log
// (standard page envelope). One row per goods × channel attempt; errors are
// whatever Postiz returned, verbatim.

const statusTag = (status?: string): React.ReactNode => {
  if (!status) return <span className='text-muted'>—</span>;
  const s = status.toLowerCase();
  const tag = s.includes('fail') || s.includes('error') ? 'danger' : s.includes('sched') || s.includes('queue') ? 'primary' : 'success';
  return <Tag tag={tag}>{status}</Tag>;
};

const PostizHistory: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const { data, isLoading, isFetching, isError, error } = useGetPostizLogQuery({ page, limit });

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? (limit > 0 ? Math.ceil(total / limit) : 0);
  const errStatus = (error as { status?: number | string })?.status;

  return (
    <>
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
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load publish history{errStatus ? ` (${errStatus})` : ''}.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Goods</th>
            <th>Channel</th>
            <th>Scheduled for</th>
            <th>Status</th>
            <th>Postiz post</th>
            <th>Error</th>
            <th>Created</th>
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
                Nothing published yet.
              </td>
            </tr>
          ) : (
            list.map((row, i) => (
              <tr key={row.id ?? i}>
                <td>
                  {row.goodsId != null ? (
                    <Link to={`/admin/goods/${row.goodsId}/insight`}>Goods #{row.goodsId}</Link>
                  ) : (
                    <span className='text-muted'>—</span>
                  )}
                </td>
                <td>
                  {row.identifier || row.integrationId || <span className='text-muted'>—</span>}
                  {row.identifier && row.integrationId && <span className='text-muted small ms-1'>({row.integrationId})</span>}
                </td>
                <td>{fmtDateTime(row.scheduleTime)}</td>
                <td>{statusTag(row.status)}</td>
                <td>{row.postizPostId ? <code>{row.postizPostId}</code> : <span className='text-muted'>—</span>}</td>
                <td className='small'>{row.error || <span className='text-muted'>—</span>}</td>
                <td>{fmtDateTime(row.addTime)}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </>
  );
};

export default PostizHistory;
