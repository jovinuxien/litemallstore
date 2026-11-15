import {
  ISeasonCandidate,
  ISeasonRunResult,
  useDismissSeasonCandidateMutation,
  useGetSeasonCandidatesQuery,
  useGetSeasonRulesQuery,
  useRestoreSeasonCandidateMutation,
  useRunSeasonCandidatesMutation,
} from 'app/shared/reducers/private/services/insightApi';
import { errnoMessage, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { fmtMoney, fmtPct } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import {
  CANDIDATE_STATUSES,
  canDismiss,
  canRestore,
  fmtCut,
  fmtRejections,
  pageCount,
  pageOf,
  parseReasons,
  statusLabel,
  statusTag,
  tierTag,
} from 'app/views/adminViews/adminModule/Insight/seasonFormat';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Seasonal candidacy (/srv/private/admin/insight/season-candidates). The
// nightly scorer (04:35) scores EVERY season and publishes on its own, so this
// panel has no "approve": an admin vetoes a product for a season (Dismiss —
// permanent, re-written forward on every run) or undoes that (Restore). Tabs
// come from the rule rows, so a fifth season needs no code change. The list
// endpoint is unpaged (a season can score ~1,000 rows) — paged here.

const PAGE_SIZE = 50;

type Pending = { goodsId: number; action: 'dismiss' | 'restore' } | null;

const num = (v?: number | null): string => (v == null ? '—' : String(v));

const SeasonCandidateList: React.FC = () => {
  const { data: rules, isLoading: rulesLoading, isError: rulesError } = useGetSeasonRulesQuery();
  const [chosen, setChosen] = React.useState<string>('');
  const season = chosen || rules?.[0]?.seasonKey || '';
  // '' = the season's latest scoring day (server default) / every status.
  const [day, setDay] = React.useState<string>('');
  const [status, setStatus] = React.useState<string>('');
  const [page, setPage] = React.useState(1);

  const { data, isLoading, isFetching, isError, error } = useGetSeasonCandidatesQuery(
    { season, day: day || undefined, status: status || undefined },
    { skip: !season },
  );
  const [dismiss, { isLoading: dismissing }] = useDismissSeasonCandidateMutation();
  const [restore, { isLoading: restoring }] = useRestoreSeasonCandidateMutation();
  const [run, { isLoading: running }] = useRunSeasonCandidatesMutation();

  const [pending, setPending] = React.useState<Pending>(null);
  const [confirmRun, setConfirmRun] = React.useState(false);
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [runResult, setRunResult] = React.useState<ISeasonRunResult[] | null>(null);

  const list = data?.list ?? [];
  const pages = pageCount(list.length, PAGE_SIZE);
  const current = Math.min(page, pages);
  const rows = pageOf(list, current, PAGE_SIZE);
  const errStatus = (error as { status?: number | string })?.status;
  const rule = (rules ?? []).find(r => r.seasonKey === season);

  const reset = () => {
    setPage(1);
    setPending(null);
    setActionError(null);
  };

  // Transport/HTTP errors and errno failures alike; the server's words verbatim.
  const failure = (res: { data?: unknown; error?: unknown }): string | null => {
    if (res.error) {
      const body = (res.error as { data?: unknown })?.data;
      const s = (res.error as { status?: number | string })?.status;
      const msg = body && typeof body === 'object' ? errnoMessage(body) : null;
      return msg && msg !== 'Request failed.' ? msg : `Request failed${s ? ` (${s})` : ''}.`;
    }
    return errnoMessage(res.data);
  };

  const decide = async (c: ISeasonCandidate, action: 'dismiss' | 'restore') => {
    setActionError(null);
    const command = { goodsId: c.goodsId, season, day: c.day || data?.day };
    const msg = failure(await (action === 'dismiss' ? dismiss(command) : restore(command)));
    setPending(null);
    if (msg) setActionError(`#${c.goodsId}: ${msg}`);
  };

  const onRun = async () => {
    setConfirmRun(false);
    setActionError(null);
    setRunResult(null);
    const res = await run();
    const msg = failure(res);
    if (msg) {
      setActionError(msg);
      return;
    }
    setRunResult((res as { data?: { data?: { seasons?: ISeasonRunResult[] } } }).data?.data?.seasons ?? []);
  };

  const busy = dismissing || restoring;

  return (
    <div className='app-container'>
      {rulesError && <div className='alert alert-danger'>Failed to load the season rules — the season tabs cannot be shown.</div>}
      {!rulesLoading && !rulesError && (rules ?? []).length === 0 && <div className='alert alert-warning'>No season rules exist yet.</div>}

      <ul className='nav nav-tabs mb-3'>
        {(rules ?? []).map(r => (
          <li className='nav-item' key={r.seasonKey}>
            <button
              type='button'
              className={`nav-link${season === r.seasonKey ? ' active' : ''}`}
              onClick={() => {
                setChosen(r.seasonKey);
                reset();
              }}
            >
              {r.name || r.seasonKey}
              {r.enabled === false && <span className='text-muted small ms-1'>(off)</span>}
            </button>
          </li>
        ))}
      </ul>

      <div className='filter-container'>
        <h4 className='mb-0 filter-item'>Season candidates</h4>
        <input
          type='date'
          className='form-control filter-item'
          style={{ width: 170 }}
          value={day}
          onChange={e => {
            setDay(e.target.value);
            reset();
          }}
          aria-label='Scoring day (empty = latest)'
        />
        <select
          className='form-select filter-item'
          style={{ width: 170 }}
          value={status}
          onChange={e => {
            setStatus(e.target.value);
            reset();
          }}
          aria-label='Status filter'
        >
          <option value=''>All statuses</option>
          {CANDIDATE_STATUSES.map(s => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
        {confirmRun ? (
          <span className='filter-item'>
            <span className='me-2'>Score every season now? It replaces today&apos;s scores and can take a minute.</span>
            <button className='btn btn-sm btn-primary me-1' type='button' onClick={onRun}>
              Run scorer
            </button>
            <button className='btn btn-sm btn-outline-secondary' type='button' onClick={() => setConfirmRun(false)}>
              Cancel
            </button>
          </span>
        ) : (
          <button className='btn btn-outline-secondary filter-item' type='button' disabled={running} onClick={() => setConfirmRun(true)}>
            {running ? 'Scoring…' : 'Run scorer now'}
          </button>
        )}
        <Link className='btn btn-outline-secondary filter-item' to='/admin/goods/season-rules'>
          Season rules
        </Link>
        {isFetching && <Spinner />}
      </div>
      <p className='text-muted small'>
        Products are scored for every season each night and published automatically — the storefront shows the top 24 per season.
        {data?.day ? ` Showing ${data.day}: ${list.length} candidate${list.length === 1 ? '' : 's'}.` : ''} Dismiss removes a product from this
        season until you restore it.
        {rule?.enabled === false && ' This season is switched off in its rule, so nothing is published for it.'}
      </p>

      {isError && <div className='alert alert-danger'>Failed to load season candidates{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      {runResult && (
        <div className='alert alert-info'>
          <div className='d-flex justify-content-between'>
            <strong>Scoring run finished</strong>
            <button type='button' className='btn-close' aria-label='Dismiss run summary' onClick={() => setRunResult(null)} />
          </div>
          {runResult.length === 0 ? (
            <div>No season was scored.</div>
          ) : (
            <table className='el-table mt-2'>
              <thead>
                <tr>
                  <th>Season</th>
                  <th className='text-end'>Scanned</th>
                  <th className='text-end'>Scored</th>
                  <th className='text-end'>Published</th>
                  <th className='text-end'>Dropped by cap</th>
                  <th className='text-end' title='Search hits discarded, not products — one product can be discarded under two terms'>
                    Hits discarded (relaxed / off-title / excluded)
                  </th>
                  <th className='text-end'>Tier cuts (hot / featured)</th>
                  <th>Rejected</th>
                </tr>
              </thead>
              <tbody>
                {runResult.map(r => (
                  <tr key={r.seasonKey}>
                    <td>{r.seasonKey}</td>
                    <td className='text-end'>{num(r.scanned)}</td>
                    <td className='text-end'>{num(r.scored)}</td>
                    <td className='text-end'>{num(r.published)}</td>
                    <td className='text-end'>{num(r.droppedByCap)}</td>
                    <td className='text-end'>
                      {num(r.discovery?.discardedRelaxed)} / {num(r.discovery?.discardedOffTitle)} / {num(r.discovery?.discardedExcluded)}
                    </td>
                    <td className='text-end'>
                      {fmtCut(r.tierCuts?.hot)} / {fmtCut(r.tierCuts?.featured)}
                    </td>
                    <td>{fmtRejections(r.rejections)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Goods</th>
            <th>Tier</th>
            <th className='text-end'>Score</th>
            <th className='text-end'>Retail</th>
            <th className='text-end'>Cost</th>
            <th className='text-end'>Margin</th>
            <th>Status</th>
            <th>Why</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading || rulesLoading ? (
            <tr>
              <td colSpan={9} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : isError ? null : rows.length === 0 ? (
            <tr>
              <td colSpan={9} className='text-center text-muted py-5'>
                {data?.day || day ? `No candidates for ${day || data?.day}.` : 'This season has not been scored yet.'}
              </td>
            </tr>
          ) : (
            rows.map(c => {
              const reasons = parseReasons(c.reasons);
              const asking = pending?.goodsId === c.goodsId ? pending.action : null;
              return (
                <tr key={c.goodsId}>
                  <td>
                    {c.picUrl && (
                      <img src={c.picUrl} alt='' style={{ width: 40, height: 40, objectFit: 'cover', borderRadius: 4 }} className='me-2' />
                    )}
                    <Link to={`/admin/goods/${c.goodsId}/insight`}>{c.name || `Goods #${c.goodsId}`}</Link>
                    <span className='text-muted small ms-1'>#{c.goodsId}</span>
                  </td>
                  <td>{c.tier ? <Tag tag={tierTag(c.tier)}>{c.tier}</Tag> : <span className='text-muted'>—</span>}</td>
                  <td className='text-end'>{c.score != null ? Number(c.score).toFixed(1) : '—'}</td>
                  <td className='text-end'>{fmtMoney(c.retailPrice)}</td>
                  <td className='text-end'>{fmtMoney(c.cost)}</td>
                  <td className='text-end'>{fmtPct(c.marginPct)}</td>
                  <td>
                    <Tag tag={statusTag(c.status)}>{statusLabel(c.status)}</Tag>
                  </td>
                  <td>
                    {reasons.length > 0 ? (
                      <span className='small text-muted' title={reasons.join('\n')}>
                        {reasons.join('; ')}
                      </span>
                    ) : (
                      <span className='text-muted'>—</span>
                    )}
                  </td>
                  <td className='text-end'>
                    {asking ? (
                      <>
                        <span className='small me-2'>
                          {asking === 'dismiss'
                            ? 'Remove from this season until restored?'
                            : 'Let the next scoring run consider it again?'}
                        </span>
                        <button className='btn btn-sm btn-danger me-1' disabled={busy} onClick={() => decide(c, asking)}>
                          {asking === 'dismiss' ? 'Confirm dismiss' : 'Confirm restore'}
                        </button>
                        <button className='btn btn-sm btn-outline-secondary' disabled={busy} onClick={() => setPending(null)}>
                          Cancel
                        </button>
                      </>
                    ) : (
                      <>
                        {canDismiss(c.status) && (
                          <button
                            className='btn btn-sm btn-outline-danger'
                            disabled={busy}
                            onClick={() => setPending({ goodsId: c.goodsId, action: 'dismiss' })}
                          >
                            Dismiss
                          </button>
                        )}
                        {canRestore(c.status) && (
                          <button
                            className='btn btn-sm btn-outline-success'
                            disabled={busy}
                            onClick={() => setPending({ goodsId: c.goodsId, action: 'restore' })}
                          >
                            Restore
                          </button>
                        )}
                      </>
                    )}
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      {pages > 1 && (
        <div className='d-flex align-items-center gap-2 mt-3'>
          <button className='btn btn-sm btn-outline-secondary' disabled={current <= 1} onClick={() => setPage(current - 1)}>
            Previous
          </button>
          <span className='small text-muted'>
            Page {current} of {pages} · {list.length} candidates
          </span>
          <button className='btn btn-sm btn-outline-secondary' disabled={current >= pages} onClick={() => setPage(current + 1)}>
            Next
          </button>
        </div>
      )}
    </div>
  );
};

export default SeasonCandidateList;
