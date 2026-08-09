import { ILedgerRow, usePromoterLedgerQuery } from 'app/shared/reducers/private/services/adminAffiliateApi';
import { ElTag, PAGE_SIZES, Pagination, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link, useParams } from 'react-router-dom';
import { money } from 'app/shared/util/money';

// Per-affiliate brokerage ledger drill-down (Wave 5, read-only): the V7
// litemall_user_brokerage_record rows for one promoter, served by the edge at
// /srv/private/admin/promoter/ledger. The write side lives in order's
// BrokerageService — this is an audit view.

const when = (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 19) : '—');

const statusTag = (row: ILedgerRow): { tag: ElTag; label: string } => {
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

const PromoterLedger: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const userId = Number(id);
  const [page, setPage] = React.useState(1);
  const [limit] = React.useState(PAGE_SIZES[1]);
  const { data, isLoading, isFetching, isError } = usePromoterLedgerQuery({ userId, page, limit }, { skip: !Number.isFinite(userId) });

  const rows = data?.list ?? [];
  const user = data?.user;

  return (
    <div className='app-container'>
      <div className='d-flex align-items-center mb-3'>
        <h5 className='mb-0'>
          Brokerage ledger{user ? ` — ${user.username || user.id}` : ''}
        </h5>
        <Link className='btn btn-sm btn-outline-secondary ms-auto' to='/admin/affiliate/promoter'>
          ‹ Back to promoters
        </Link>
      </div>

      {user && (
        <div className='mb-3 text-muted'>
          Balance <strong>{money(user.brokeragePrice)}</strong> · referrals <strong>{user.spreadCount ?? 0}</strong> · paid orders{' '}
          <strong>{user.payCount ?? 0}</strong>
        </div>
      )}

      {isError && <div className='alert alert-danger'>Failed to load the ledger.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Date</th>
            <th>Direction</th>
            <th>Reference</th>
            <th>Amount</th>
            <th>Balance after</th>
            <th>Status</th>
            <th>Freeze → unfreeze</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={7} className='text-center py-4'>
                <span className='spinner-border spinner-border-sm text-primary' role='status' />
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td colSpan={7} className='text-center text-muted py-4'>
                No ledger rows for this user.
              </td>
            </tr>
          ) : (
            rows.map(row => {
              const st = statusTag(row);
              const credit = row.pm !== 0;
              return (
                <tr key={row.id}>
                  <td>{when(row.add_time)}</td>
                  <td>{credit ? 'Credit' : 'Debit'}</td>
                  <td>
                    {row.title || row.link_type || '—'}
                    {row.link_id ? <div className='text-muted small'>{row.link_id}</div> : null}
                  </td>
                  <td style={{ color: credit ? '#67c23a' : '#f56c6c' }}>
                    {credit ? '+' : '−'}
                    {money(row.price)}
                  </td>
                  <td>{money(row.balance)}</td>
                  <td>
                    <Tag tag={st.tag}>{st.label}</Tag>
                  </td>
                  <td>
                    {when(row.freeze_time)} → {when(row.unfreeze_time)}
                  </td>
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

export default PromoterLedger;
