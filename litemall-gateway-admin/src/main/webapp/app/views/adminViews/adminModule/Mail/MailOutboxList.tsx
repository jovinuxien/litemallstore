import {
  IMailOutbox,
  MAIL_STATUSES,
  MailStatus,
  mailOpMessage,
  useListMailOutboxQuery,
  useResendMailMutation,
} from 'app/shared/reducers/private/services/adminMailApi';
import { ElTag, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// Wave 6: customer-mail outbox admin panel, authenticated admin →
// litemall-order /srv/private/admin/mail/list (the litemall_mail_outbox
// ledger written by the transactional-mail listeners). Status filter,
// last_error tooltip, and Resend on failed rows (failed→pending reset with
// attempts zeroed — the sweep re-delivers).

const STATUS_TAG: Record<MailStatus, { tag: ElTag; text: string }> = {
  pending: { tag: 'warning', text: 'pending' },
  sent: { tag: 'success', text: 'sent' },
  failed: { tag: 'danger', text: 'failed' },
};

const fmt = (v?: string): string => (v ? v.replace('T', ' ') : '—');

const MailOutboxList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [status, setStatus] = React.useState<MailStatus | ''>('');

  const { data, isLoading, isFetching, isError, error } = useListMailOutboxQuery({ page, limit, status });
  const [resendMail, { isLoading: resending }] = useResendMailMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const errStatus = (error as { status?: number | string })?.status;

  const onResend = async (row: IMailOutbox) => {
    if (row.id == null) return;
    setActionError(null);
    const res = await resendMail(row.id);
    setActionError(mailOpMessage(res));
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <select
          className='form-select filter-item'
          style={{ width: 140 }}
          value={status}
          onChange={e => {
            setPage(1);
            setStatus(e.target.value as MailStatus | '');
          }}
          aria-label='Status filter'
        >
          <option value=''>All statuses</option>
          {MAIL_STATUSES.map(s => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
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
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load mail outbox{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Recipient</th>
            <th>Subject</th>
            <th>Template</th>
            <th>Status</th>
            <th className='text-end'>Attempts</th>
            <th>Send at</th>
            <th>Queued</th>
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
                No outbox rows found.
              </td>
            </tr>
          ) : (
            list.map(row => {
              const st = row.status ? STATUS_TAG[row.status] : undefined;
              return (
                <tr key={row.id}>
                  <td>{row.recipient || '—'}</td>
                  <td style={{ maxWidth: 280 }} className='text-truncate' title={row.subject}>
                    {row.subject || '—'}
                  </td>
                  <td>{row.templateKey ? <Tag tag='info'>{row.templateKey}</Tag> : '—'}</td>
                  <td>
                    {/* lastError also surfaces on pending rows with attempts>0 — a retry in progress (handoff note) */}
                    <span title={row.lastError && (row.status === 'failed' || (row.attempts ?? 0) > 0) ? row.lastError : undefined}>
                      {st ? <Tag tag={st.tag}>{st.text}</Tag> : <Tag tag='info'>{row.status ?? 'unknown'}</Tag>}
                    </span>
                    {row.status === 'failed' && row.lastError && (
                      <div className='small text-danger text-truncate' style={{ maxWidth: 220 }} title={row.lastError}>
                        {row.lastError}
                      </div>
                    )}
                  </td>
                  <td className='text-end'>{row.attempts ?? 0}</td>
                  <td className='small'>{fmt(row.sendAt)}</td>
                  <td className='small'>{fmt(row.addTime)}</td>
                  <td className='text-end'>
                    {row.status === 'failed' && (
                      <button className='btn btn-sm btn-outline-primary' disabled={resending} onClick={() => onResend(row)}>
                        Resend
                      </button>
                    )}
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={data?.pages} total={data?.total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default MailOutboxList;
