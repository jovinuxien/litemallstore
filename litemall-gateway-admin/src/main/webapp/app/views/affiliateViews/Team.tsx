import { useListTeamQuery } from 'app/shared/reducers/private/services/affiliateApi';
import { PAGE_SIZES, Pagination } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// Team (Wave 5): users whose spread_uid points at me, from
// GET /srv/private/affiliate/team. Nicknames arrive MASKED from the server —
// an affiliate never sees another user's full identity.

const when = (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 19) : '—');

const AffiliateTeam: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(PAGE_SIZES[0]);
  const { data, isLoading, isFetching, isError } = useListTeamQuery({ page, limit });

  const rows = data?.list ?? [];

  return (
    <div className='app-container' style={{ maxWidth: 720 }}>
      <h5 className='mb-3'>My team</h5>
      {isError && <div className='alert alert-danger'>Failed to load your team. Please try again.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Member</th>
            <th>Joined</th>
            <th>Paid orders</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={3} className='text-center py-4'>
                <span className='spinner-border spinner-border-sm text-primary' role='status' />
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td colSpan={3} className='text-center text-muted py-4'>
                No referrals yet — new users who register through your invite link appear here.
              </td>
            </tr>
          ) : (
            rows.map((row, i) => (
              <tr key={`${row.nickname}-${i}`}>
                <td>{row.nickname || '—'}</td>
                <td>{when(row.addTime)}</td>
                <td>{row.payCount ?? 0}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={data?.pages} total={data?.total} rowCount={rows.length} limit={limit} busy={isFetching} onPage={setPage} />

      <div className='d-flex align-items-center mt-2'>
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
    </div>
  );
};

export default AffiliateTeam;
