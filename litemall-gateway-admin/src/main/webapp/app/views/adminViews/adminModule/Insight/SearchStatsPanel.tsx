import {
  ISearchQueryStat,
  useGetSearchStatsQuery,
  useRefreshSearchTrendingMutation,
  useRunSearchStatsRollupMutation,
} from 'app/shared/reducers/private/services/insightApi';
import { Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { ctrOf, fmtCount, fmtCtr, fmtZeroShare, overallCtrPct, summaryText } from './searchStatsFormat';
import * as React from 'react';

// Wave 22: search demand analytics (/srv/private/admin/insight/search-stats,
// goods-management V57 nightly rollup over search history + the consent-ramped
// behavioral log). Aggregate-only — no per-visitor drill-down, and no money
// on this panel. "Refresh trending" recomputes the storefront hot-keyword set
// from real demand (additive — curated admin keywords are preserved);
// "Run rollup" re-runs the nightly aggregation on demand (dev/admin).

const WINDOWS: (7 | 30)[] = [7, 30];

const QueryTable: React.FC<{ rows: ISearchQueryStat[]; loading: boolean; emptyText: string }> = ({ rows, loading, emptyText }) => (
  <table className='el-table'>
    <thead>
      <tr>
        <th>Keyword</th>
        <th className='text-end'>Searches</th>
        <th className='text-end'>Clicks</th>
        <th className='text-end'>CTR</th>
        <th className='text-end'>Zero results</th>
      </tr>
    </thead>
    <tbody>
      {loading ? (
        <tr>
          <td colSpan={5} className='text-center p-5'>
            <span className='spinner-border text-primary' role='status' />
          </td>
        </tr>
      ) : rows.length === 0 ? (
        <tr>
          <td colSpan={5} className='text-center text-muted py-4'>
            {emptyText}
          </td>
        </tr>
      ) : (
        rows.map(row => (
          <tr key={row.keyword ?? ''}>
            <td>{row.keyword || <span className='text-muted'>—</span>}</td>
            <td className='text-end'>{fmtCount(row.searches)}</td>
            <td className='text-end'>{fmtCount(row.clicks)}</td>
            <td className='text-end'>{fmtCtr(ctrOf(row))}</td>
            <td className='text-end'>{fmtCount(row.zeroResults)}</td>
          </tr>
        ))
      )}
    </tbody>
  </table>
);

const TotalCard: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <div className='col-6 col-md-3 mb-3'>
    <div className='card h-100'>
      <div className='card-body py-3'>
        <div className='text-muted small'>{label}</div>
        <div className='fs-4 fw-semibold'>{value}</div>
      </div>
    </div>
  </div>
);

const SearchStatsPanel: React.FC = () => {
  const [days, setDays] = React.useState<7 | 30>(7);
  const { data, isLoading, isFetching, isError, error } = useGetSearchStatsQuery({ days });
  const [refreshTrending, { isLoading: refreshing }] = useRefreshSearchTrendingMutation();
  const [runRollup, { isLoading: rolling }] = useRunSearchStatsRollupMutation();
  const [notice, setNotice] = React.useState<{ ok: boolean; text: string } | null>(null);

  const errStatus = (error as { status?: number | string })?.status;
  const totals = data?.totals;

  // Surface a mutation result: transport errors, errno failures, or the
  // operation summary from the envelope data.
  const runOp = async (op: () => Promise<{ data?: unknown; error?: unknown }>, fallback: string) => {
    setNotice(null);
    const res = await op();
    if (res.error) {
      const s = (res.error as { status?: number | string })?.status;
      setNotice({ ok: false, text: `Request failed${s ? ` (${s})` : ''}.` });
      return;
    }
    const env = res.data as { errno?: number; errmsg?: string; data?: unknown } | undefined;
    if (env && typeof env.errno === 'number' && env.errno !== 0) {
      setNotice({ ok: false, text: env.errmsg || `Request failed (errno ${env.errno})` });
      return;
    }
    setNotice({ ok: true, text: summaryText(env?.data, fallback) });
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <h4 className='mb-0 filter-item'>Search analytics</h4>
        <div className='btn-group filter-item' role='group' aria-label='Window'>
          {WINDOWS.map(w => (
            <button
              key={w}
              type='button'
              className={`btn btn-sm ${days === w ? 'btn-primary' : 'btn-outline-primary'}`}
              onClick={() => setDays(w)}
            >
              {w} days
            </button>
          ))}
        </div>
        <button className='btn btn-outline-success filter-item' type='button' disabled={refreshing} onClick={() => void runOp(() => refreshTrending(), 'Trending keywords refreshed from real demand.')}>
          {refreshing ? 'Refreshing…' : 'Refresh trending'}
        </button>
        <button className='btn btn-outline-secondary filter-item' type='button' disabled={rolling} onClick={() => void runOp(() => runRollup(), 'Rollup completed.')}>
          {rolling ? 'Rolling up…' : 'Run rollup'}
        </button>
        {isFetching && <Spinner />}
      </div>
      <p className='text-muted small'>
        Aggregated from search history and consented behavioral events (nightly rollup) — no per-visitor data. Refresh trending recomputes the
        storefront hot keywords from real demand; curated keywords are preserved.
      </p>

      {isError && <div className='alert alert-danger'>Failed to load search stats{errStatus ? ` (${errStatus})` : ''}.</div>}
      {notice && <div className={`alert ${notice.ok ? 'alert-success' : 'alert-danger'}`}>{notice.text}</div>}

      <div className='row'>
        <TotalCard label='Searches' value={fmtCount(totals?.searches)} />
        <TotalCard label='Zero-result searches' value={fmtZeroShare(totals)} />
        <TotalCard label='Result clicks' value={fmtCount(totals?.clicks)} />
        <TotalCard label='Overall CTR' value={fmtCtr(overallCtrPct(totals))} />
      </div>

      <h6 className='mt-3'>Top queries — last {days} days</h6>
      <QueryTable rows={data?.topQueries ?? []} loading={isLoading} emptyText='No searches recorded in this window yet.' />

      <h6 className='mt-4'>Zero-result queries — last {days} days</h6>
      <p className='text-muted small mb-2'>Demand the catalog is not answering — sourcing/synonym candidates.</p>
      <QueryTable rows={data?.zeroResultQueries ?? []} loading={isLoading} emptyText='No zero-result queries in this window.' />
    </div>
  );
};

export default SearchStatsPanel;
