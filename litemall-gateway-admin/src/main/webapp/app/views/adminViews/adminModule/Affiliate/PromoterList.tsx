import {
  IPromoterRow,
  useListPromotersQuery,
  useTogglePromoterMutation,
} from 'app/shared/reducers/private/services/adminAffiliateApi';
import { PAGE_SIZES, Pagination, Spinner, Tag, errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';
import { money } from 'app/shared/util/money';

// Promoters (Wave 5): search litemall_user and grant/revoke the is_promoter
// flag — promoters are ADMIN-GRANTED only (locked design; no self-serve
// signup). Revoking also kills the user's affiliate sessions edge-side. Each
// row links to the read-only brokerage ledger drill-down.

const when = (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 19) : '—');

const PromoterList: React.FC = () => {
  const [qInput, setQInput] = React.useState('');
  const [q, setQ] = React.useState('');
  const [promotersOnly, setPromotersOnly] = React.useState(false);
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(PAGE_SIZES[0]);
  const { data, isLoading, isFetching, isError } = useListPromotersQuery({ q: q || undefined, promotersOnly, page, limit });
  const [togglePromoter, { isLoading: toggling }] = useTogglePromoterMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const search = (e: React.FormEvent) => {
    e.preventDefault();
    setQ(qInput.trim());
    setPage(1);
  };

  const onToggle = async (row: IPromoterRow) => {
    const grant = !row.isPromoter;
    if (!grant && !window.confirm(`Revoke affiliate status for "${row.username || row.id}"? Their portal sessions will be signed out.`)) {
      return;
    }
    setActionError(null);
    const res = await togglePromoter({ userId: row.id, promoter: grant });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  const rows = data?.list ?? [];

  return (
    <div className='app-container'>
      <h5 className='mb-3'>Promoters</h5>
      {isError && <div className='alert alert-danger'>Failed to load users.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <form className='d-flex align-items-center gap-2 mb-3 flex-wrap' onSubmit={search}>
        <input
          className='form-control'
          style={{ maxWidth: 280 }}
          placeholder='Username / nickname / mobile'
          value={qInput}
          onChange={e => setQInput(e.target.value)}
        />
        <button className='btn btn-primary' type='submit'>
          Search
        </button>
        <div className='form-check ms-2'>
          <input
            id='promoters-only'
            className='form-check-input'
            type='checkbox'
            checked={promotersOnly}
            onChange={e => {
              setPromotersOnly(e.target.checked);
              setPage(1);
            }}
          />
          <label className='form-check-label' htmlFor='promoters-only'>
            Promoters only
          </label>
        </div>
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
      </form>

      <table className='el-table'>
        <thead>
          <tr>
            <th>Id</th>
            <th>User</th>
            <th>Mobile</th>
            <th>Affiliate</th>
            <th>Referrals</th>
            <th>Paid orders</th>
            <th>Balance</th>
            <th>Registered</th>
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
                No users found.
              </td>
            </tr>
          ) : (
            rows.map(row => (
              <tr key={row.id}>
                <td>{row.id}</td>
                <td>
                  {row.username || '—'}
                  {row.nickname && row.nickname !== row.username ? <div className='text-muted small'>{row.nickname}</div> : null}
                </td>
                <td>{row.mobile || '—'}</td>
                <td>{row.isPromoter ? <Tag tag='success'>Promoter</Tag> : <Tag tag='info'>No</Tag>}</td>
                <td>{row.spreadCount ?? 0}</td>
                <td>{row.payCount ?? 0}</td>
                <td>{money(row.brokeragePrice)}</td>
                <td>{when(row.addTime)}</td>
                <td>
                  <button
                    type='button'
                    className={`btn btn-sm ${row.isPromoter ? 'btn-outline-danger' : 'btn-outline-success'} me-2`}
                    disabled={toggling}
                    onClick={() => onToggle(row)}
                  >
                    {row.isPromoter ? 'Revoke' : 'Grant'}
                  </button>
                  <Link className='btn btn-sm btn-outline-secondary' to={`/admin/affiliate/promoter/${row.id}/ledger`}>
                    Ledger
                  </Link>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={data?.pages} total={data?.total} rowCount={rows.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default PromoterList;
