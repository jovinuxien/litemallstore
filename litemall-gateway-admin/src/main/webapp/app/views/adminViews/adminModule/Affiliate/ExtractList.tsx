import {
  IAdminExtractRow,
  useApproveExtractMutation,
  useListExtractsQuery,
  useRejectExtractMutation,
} from 'app/shared/reducers/private/services/adminAffiliateApi';
import { ElTag, PAGE_SIZES, Pagination, Spinner, Tag, errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// Withdrawal approval console (Wave 5): litemall_user_extract requests served
// by order at /srv/private/admin/extract/*. Approve only transitions PENDING
// rows (guarded server-side); reject requires a reason, refunds the user's
// brokerage balance and stores the reason as fail_msg (visible to the
// affiliate in their history).

const money = (v?: number) => (v == null ? '—' : `$${Number(v).toFixed(2)}`);
const when = (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 19) : '—');

const statusTag = (status?: number): { tag: ElTag; label: string } => {
  switch (status) {
    case 0:
      return { tag: 'warning', label: 'Pending' };
    case 1:
      return { tag: 'primary', label: 'Processing' };
    case 2:
      return { tag: 'success', label: 'Completed' };
    case -1:
      return { tag: 'danger', label: 'Rejected' };
    default:
      return { tag: 'info', label: String(status ?? '—') };
  }
};

const STATUS_FILTERS = [
  { value: '', label: 'All' },
  { value: '0', label: 'Pending' },
  { value: '1', label: 'Processing' },
  { value: '2', label: 'Completed' },
  { value: '-1', label: 'Rejected' },
];

const ExtractList: React.FC = () => {
  const [status, setStatus] = React.useState('0');
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(PAGE_SIZES[0]);
  const { data, isLoading, isFetching, isError } = useListExtractsQuery({ status: status === '' ? undefined : status, page, limit });
  const [approveExtract, { isLoading: approving }] = useApproveExtractMutation();
  const [rejectExtract, { isLoading: rejecting }] = useRejectExtractMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [actionOk, setActionOk] = React.useState<string | null>(null);

  const busy = approving || rejecting;

  const onApprove = async (row: IAdminExtractRow) => {
    if (!row.id || !window.confirm(`Approve payout of ${money(row.extractPrice)} to "${row.realName || row.userId}"?`)) return;
    setActionError(null);
    setActionOk(null);
    const res = await approveExtract({ id: row.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
    else setActionOk(`Request #${row.id} approved.`);
  };

  const onReject = async (row: IAdminExtractRow) => {
    if (!row.id) return;
    const reason = window.prompt(`Reject request #${row.id} — reason shown to the affiliate:`);
    if (reason == null) return;
    if (!reason.trim()) {
      setActionError('A rejection reason is required.');
      return;
    }
    setActionError(null);
    setActionOk(null);
    const res = await rejectExtract({ id: row.id, reason: reason.trim() });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
    else setActionOk(`Request #${row.id} rejected — balance refunded.`);
  };

  const rows = data?.list ?? [];

  return (
    <div className='app-container'>
      <h5 className='mb-3'>Withdrawal requests</h5>
      {isError && <div className='alert alert-danger'>Failed to load withdrawal requests.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}
      {actionOk && <div className='alert alert-success'>{actionOk}</div>}

      <div className='d-flex align-items-center gap-2 mb-3'>
        <select
          className='form-select'
          style={{ maxWidth: 180 }}
          value={status}
          onChange={e => {
            setStatus(e.target.value);
            setPage(1);
          }}
        >
          {STATUS_FILTERS.map(f => (
            <option key={f.value} value={f.value}>
              {f.label}
            </option>
          ))}
        </select>
        {isFetching && <Spinner />}
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
            <th>Id</th>
            <th>User</th>
            <th>Payee</th>
            <th>Method</th>
            <th>Account</th>
            <th>Amount</th>
            <th>Requested</th>
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={9} className='text-center py-4'>
                <span className='spinner-border spinner-border-sm text-primary' role='status' />
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td colSpan={9} className='text-center text-muted py-4'>
                No withdrawal requests.
              </td>
            </tr>
          ) : (
            rows.map(row => {
              const st = statusTag(row.status);
              return (
                <tr key={row.id}>
                  <td>{row.id}</td>
                  <td>{row.userId ?? '—'}</td>
                  <td>{row.realName || '—'}</td>
                  <td>{row.extractType || '—'}</td>
                  <td>
                    {row.bankCode || '—'}
                    {row.bankAddress ? <div className='text-muted small'>{row.bankAddress}</div> : null}
                  </td>
                  <td>{money(row.extractPrice)}</td>
                  <td>{when(row.addTime)}</td>
                  <td>
                    <Tag tag={st.tag}>{st.label}</Tag>
                    {row.status === -1 && row.failMsg ? <div className='text-muted small'>{row.failMsg}</div> : null}
                  </td>
                  <td>
                    {row.status === 0 ? (
                      <>
                        <button type='button' className='btn btn-sm btn-outline-success me-2' disabled={busy} onClick={() => onApprove(row)}>
                          Approve
                        </button>
                        <button type='button' className='btn btn-sm btn-outline-danger' disabled={busy} onClick={() => onReject(row)}>
                          Reject
                        </button>
                      </>
                    ) : (
                      '—'
                    )}
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

export default ExtractList;
