import {
  useExtractHistoryQuery,
  useGetDashboardQuery,
  useRequestExtractMutation,
} from 'app/shared/reducers/private/services/affiliateApi';
import { ElTag, PAGE_SIZES, Pagination, Tag, errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { money } from 'app/shared/util/money';

// Withdraw (Wave 5): request a brokerage payout (POST
// /srv/private/affiliate/extract with source='brokerage' — below-minimum or
// insufficient balance come back as business errors surfaced inline) plus the
// request history with pending/completed/rejected chips and the admin's
// rejection reason.

const when = (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 19) : '—');

export const extractStatus = (status?: number): { tag: ElTag; label: string } => {
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

const EXTRACT_TYPES = [
  { value: 'bank', label: 'Bank transfer' },
  { value: 'alipay', label: 'Alipay' },
  { value: 'weixin', label: 'WeChat Pay' },
];

const AffiliateWithdraw: React.FC = () => {
  const { data: dashboard } = useGetDashboardQuery();
  const [page, setPage] = React.useState(1);
  const [limit] = React.useState(PAGE_SIZES[0]);
  const { data: history, isLoading, isFetching, isError } = useExtractHistoryQuery({ page, limit });
  const [requestExtract, { isLoading: submitting }] = useRequestExtractMutation();

  const [amount, setAmount] = React.useState('');
  const [realName, setRealName] = React.useState('');
  const [extractType, setExtractType] = React.useState(EXTRACT_TYPES[0].value);
  const [bankCode, setBankCode] = React.useState('');
  const [bankAddress, setBankAddress] = React.useState('');
  const [formError, setFormError] = React.useState<string | null>(null);
  const [submitted, setSubmitted] = React.useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setSubmitted(false);
    const value = Number(amount);
    if (!Number.isFinite(value) || value <= 0) {
      setFormError('Enter a positive amount.');
      return;
    }
    if (!realName.trim()) {
      setFormError('Enter the payee name.');
      return;
    }
    const res = await requestExtract({
      extractAmount: value,
      realName: realName.trim(),
      extractType,
      bankCode: bankCode.trim() || undefined,
      bankAddress: bankAddress.trim() || undefined,
      source: 'brokerage',
    });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setFormError(msg);
      return;
    }
    setSubmitted(true);
    setAmount('');
  };

  const rows = history?.list ?? [];

  return (
    <div className='app-container' style={{ maxWidth: 860 }}>
      <h5 className='mb-3'>Withdraw</h5>

      <div className='card mb-4'>
        <div className='card-body'>
          <div className='mb-3'>
            <span className='text-muted'>Available balance:</span>{' '}
            <span className='fs-5 fw-semibold' style={{ color: '#67c23a' }}>
              {money(dashboard?.available)}
            </span>
          </div>

          {formError && <div className='alert alert-danger'>{formError}</div>}
          {submitted && <div className='alert alert-success'>Withdrawal requested — it now awaits admin approval.</div>}

          <form onSubmit={onSubmit}>
            <div className='row g-3'>
              <div className='col-sm-4'>
                <label className='form-label' htmlFor='wd-amount'>
                  Amount
                </label>
                <input
                  id='wd-amount'
                  className='form-control'
                  type='number'
                  min='0'
                  step='0.01'
                  value={amount}
                  onChange={e => setAmount(e.target.value)}
                  required
                />
              </div>
              <div className='col-sm-4'>
                <label className='form-label' htmlFor='wd-name'>
                  Payee name
                </label>
                <input id='wd-name' className='form-control' value={realName} onChange={e => setRealName(e.target.value)} required />
              </div>
              <div className='col-sm-4'>
                <label className='form-label' htmlFor='wd-type'>
                  Payout method
                </label>
                <select id='wd-type' className='form-select' value={extractType} onChange={e => setExtractType(e.target.value)}>
                  {EXTRACT_TYPES.map(t => (
                    <option key={t.value} value={t.value}>
                      {t.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className='col-sm-6'>
                <label className='form-label' htmlFor='wd-account'>
                  Account / card number
                </label>
                <input id='wd-account' className='form-control' value={bankCode} onChange={e => setBankCode(e.target.value)} />
              </div>
              <div className='col-sm-6'>
                <label className='form-label' htmlFor='wd-bank'>
                  Bank / branch (optional)
                </label>
                <input id='wd-bank' className='form-control' value={bankAddress} onChange={e => setBankAddress(e.target.value)} />
              </div>
            </div>
            <button className='btn btn-primary mt-3' type='submit' disabled={submitting}>
              {submitting ? 'Submitting…' : 'Request withdrawal'}
            </button>
          </form>
        </div>
      </div>

      <h6 className='mb-2'>Request history</h6>
      {isError && <div className='alert alert-danger'>Failed to load your withdrawal history.</div>}
      <table className='el-table'>
        <thead>
          <tr>
            <th>Date</th>
            <th>Amount</th>
            <th>Method</th>
            <th>Status</th>
            <th>Note</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={5} className='text-center py-4'>
                <span className='spinner-border spinner-border-sm text-primary' role='status' />
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td colSpan={5} className='text-center text-muted py-4'>
                No withdrawal requests yet.
              </td>
            </tr>
          ) : (
            rows.map(row => {
              const st = extractStatus(row.status);
              return (
                <tr key={row.id}>
                  <td>{when(row.addTime)}</td>
                  <td>{money(row.extractPrice)}</td>
                  <td>{row.extractType || '—'}</td>
                  <td>
                    <Tag tag={st.tag}>{st.label}</Tag>
                  </td>
                  <td>{row.status === -1 ? row.failMsg || '—' : '—'}</td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={history?.pages} total={history?.total} rowCount={rows.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default AffiliateWithdraw;
