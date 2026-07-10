import {
  AFTERSALE_STATUS,
  IAftersale,
  aftersaleOpMessage,
  useApproveAftersaleMutation,
  useListAftersalesQuery,
  useRejectAftersaleMutation,
} from 'app/shared/reducers/private/services/adminAftersaleApi';
import { ElTag, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Aftersale/RMA admin queue — list + approve/reject, through the gateway as
// an authenticated admin (adminAftersaleApi → /srv/private/admin/aftersale).
// Approval refunds via order's tender-parity path in one transaction; reject
// closes the application and leaves the order untouched.

const STATUS_TAG: Record<number, ElTag> = {
  1: 'warning', // applied
  2: 'primary', // approved
  3: 'success', // refunded
  4: 'danger', // rejected
  5: 'info', // cancelled
};

const TYPE_LABEL: Record<number, string> = { 0: 'refund only', 1: 'return & refund' };

const AftersaleList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [status, setStatus] = React.useState<number | undefined>(undefined);
  const [orderIdInput, setOrderIdInput] = React.useState('');
  const [orderId, setOrderId] = React.useState<number | undefined>(undefined);

  const { data, isLoading, isFetching, isError, error } = useListAftersalesQuery({ page, limit, status, orderId });
  const [approveAftersale, { isLoading: approving }] = useApproveAftersaleMutation();
  const [rejectAftersale, { isLoading: rejecting }] = useRejectAftersaleMutation();
  const [actionMsg, setActionMsg] = React.useState<{ ok: boolean; text: string } | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;
  const busy = approving || rejecting;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setOrderId(orderIdInput.trim() ? Number(orderIdInput.trim()) : undefined);
  };

  const onApprove = async (a: IAftersale) => {
    if (!window.confirm(`Approve aftersale ${a.aftersaleSn ?? a.id} and refund ¥${a.amount ?? '?'} on order #${a.orderId}?`)) return;
    setActionMsg(null);
    const msg = aftersaleOpMessage(await approveAftersale(a.id as number));
    setActionMsg(msg ? { ok: false, text: msg } : { ok: true, text: `Aftersale ${a.aftersaleSn ?? a.id} approved — refund issued.` });
  };

  const onReject = async (a: IAftersale) => {
    const reason = window.prompt(`Reject aftersale ${a.aftersaleSn ?? a.id} — reason (optional):`);
    if (reason === null) return;
    setActionMsg(null);
    const msg = aftersaleOpMessage(await rejectAftersale({ id: a.id as number, reason: reason.trim() || undefined }));
    setActionMsg(msg ? { ok: false, text: msg } : { ok: true, text: `Aftersale ${a.aftersaleSn ?? a.id} rejected.` });
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <select
          className='form-select filter-item'
          style={{ width: 160 }}
          value={status ?? ''}
          onChange={e => {
            setPage(1);
            setStatus(e.target.value === '' ? undefined : Number(e.target.value));
          }}
          aria-label='Status filter'
        >
          <option value=''>All statuses</option>
          {Object.entries(AFTERSALE_STATUS).map(([code, label]) => (
            <option key={code} value={code}>
              {label}
            </option>
          ))}
        </select>
        <input
          className='form-control filter-item'
          style={{ width: 140 }}
          placeholder='Order ID'
          value={orderIdInput}
          onChange={e => setOrderIdInput(e.target.value.replace(/[^\d]/g, ''))}
        />
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
        <button className='btn btn-primary filter-item' type='submit'>
          Search
        </button>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load aftersales{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionMsg && <div className={`alert ${actionMsg.ok ? 'alert-success' : 'alert-danger'}`}>{actionMsg.text}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>SN</th>
            <th>Order</th>
            <th>User</th>
            <th>Type</th>
            <th style={{ maxWidth: 320 }}>Reason</th>
            <th className='text-end'>Amount</th>
            <th>Status</th>
            <th>Applied</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={9} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={9} className='text-center text-muted py-5'>
                No aftersale applications.
              </td>
            </tr>
          ) : (
            list.map(a => (
              <tr key={a.id}>
                <td>{a.aftersaleSn ?? a.id}</td>
                <td>
                  <Link to={`/admin/mall/order/${a.orderId}`}>#{a.orderId}</Link>
                </td>
                <td>#{a.userId}</td>
                <td>{TYPE_LABEL[a.type ?? 0] ?? a.type}</td>
                <td style={{ maxWidth: 320, whiteSpace: 'pre-wrap' }}>
                  {a.reason || '—'}
                  {a.comment && <div className='text-muted small'>admin: {a.comment}</div>}
                </td>
                <td className='text-end'>{a.amount != null ? `¥${a.amount}` : '—'}</td>
                <td>
                  <Tag tag={STATUS_TAG[a.status ?? 1] ?? 'info'}>{a.statusText || AFTERSALE_STATUS[a.status ?? 1] || a.status}</Tag>
                </td>
                <td className='text-muted small'>{a.addTime ?? '—'}</td>
                <td className='text-end'>
                  {a.status === 1 ? (
                    <>
                      <button className='btn btn-sm btn-outline-success me-1' disabled={busy} onClick={() => onApprove(a)}>
                        Approve
                      </button>
                      <button className='btn btn-sm btn-outline-danger' disabled={busy} onClick={() => onReject(a)}>
                        Reject
                      </button>
                    </>
                  ) : (
                    <span className='text-muted small'>{a.handleTime ?? '—'}</span>
                  )}
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

export default AftersaleList;
