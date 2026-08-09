import { IBrokerageRecord, useListRecordsQuery } from 'app/shared/reducers/private/services/affiliateApi';
import { ElTag, PAGE_SIZES, Pagination, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { money } from 'app/shared/util/money';

// Earnings ledger (Wave 5): the affiliate's own litemall_user_brokerage_record
// rows from GET /srv/private/affiliate/records — commissions (pm=1) and
// withdrawal debits (pm=0) with frozen/valid/invalid chips.

export const recordStatus = (row: IBrokerageRecord): { tag: ElTag; label: string } => {
  switch (row.status) {
    case 0:
      return { tag: 'warning', label: 'Frozen' };
    case 1:
      return { tag: 'success', label: 'Valid' };
    case -1:
      return { tag: 'danger', label: 'Invalid' };
    default:
      return { tag: 'info', label: String(row.status ?? '—') };
  }
};

const when = (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 19) : '—');

const AffiliateEarnings: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(PAGE_SIZES[0]);
  const { data, isLoading, isFetching, isError } = useListRecordsQuery({ page, limit });

  const rows = data?.list ?? [];

  return (
    <div className='app-container'>
      <h5 className='mb-3'>Earnings</h5>
      {isError && <div className='alert alert-danger'>Failed to load your ledger. Please try again.</div>}

      <div className='d-flex align-items-center mb-2'>
        <select
          className='form-select form-select-sm ms-auto'
          style={{ width: 'auto' }}
          value={limit}
          onChange={e => {
            setLimit(Number(e.target.value));
            setPage(1);
          }}
        >
          {PAGE_SIZES.map(s => (
            <option key={s} value={s}>
              {s} / page
            </option>
          ))}
        </select>
      </div>

      <table className='el-table'>
        <thead>
          <tr>
            <th>Date</th>
            <th>Type</th>
            <th>Reference</th>
            <th>Amount</th>
            <th>Status</th>
            <th>Unfreezes</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={6} className='text-center py-4'>
                <span className='spinner-border spinner-border-sm text-primary' role='status' />
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td colSpan={6} className='text-center text-muted py-4'>
                No commission records yet — share your invite links to start earning.
              </td>
            </tr>
          ) : (
            rows.map(row => {
              const st = recordStatus(row);
              const credit = row.pm !== 0;
              return (
                <tr key={row.id}>
                  <td>{when(row.addTime)}</td>
                  <td>{credit ? 'Commission' : 'Withdrawal'}</td>
                  <td>
                    {row.title || row.linkType || '—'}
                    {row.linkId ? <div className='text-muted small'>{row.linkId}</div> : null}
                  </td>
                  <td style={{ color: credit ? '#67c23a' : '#f56c6c' }}>
                    {credit ? '+' : '−'}
                    {money(row.price)}
                  </td>
                  <td>
                    <Tag tag={st.tag}>{st.label}</Tag>
                  </td>
                  <td>{row.status === 0 ? when(row.unfreezeTime) : '—'}</td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={data?.pages} total={data?.total} rowCount={rows.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default AffiliateEarnings;
