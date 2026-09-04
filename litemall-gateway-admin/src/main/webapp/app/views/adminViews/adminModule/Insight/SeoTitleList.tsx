import {
  ISeoTitleRow,
  SEO_BATCH_LIMIT,
  useApplySeoTitleMutation,
  useApplySeoTitlesMutation,
  useGetSeoTitlesQuery,
} from 'app/shared/reducers/private/services/adminSeoApi';
import { mutationError } from './applyResult';
import { applyWarning, batchItems, cleanRowIds, draftOf, isApplicable, summarizeBatch } from './seoTitleBatch';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// On-page SEO: product titles Google will truncate (/srv/private/admin/seo/titles).
//
// Google renders roughly the first 60 characters of a result title; everything past
// that is cut with an ellipsis, so the end of a long title is invisible to a searcher.
// The proposal is a word-boundary truncation of the CURRENT title — deliberately not a
// title generated from keyword data. The bought terms carry competitor brands and
// homonyms, and rearranging a supplier's wording around them produces copy that would
// go out under the shop's name. The terms are shown as evidence; the words are yours.
//
// Every row is editable before applying, and applying sends exactly what is in the box.
// Bulk apply exists for the rows the server did NOT flag: the header checkbox selects
// only `needsReview === false` rows, a flagged row has to be ticked by hand after reading.
// The server reports each row of a batch in place, and that report stays on screen until
// dismissed — after the list refreshes, the applied rows are gone from it, so the banner
// is the only record of what just happened.

const LENGTH_CHOICES = [50, 60, 70];

/** Google's cut point. Amber before it, red past it. */
const lengthTag = (len: number, max: number) => (len > max ? 'danger' : len > max - 10 ? 'warning' : 'success');

/** What happened to a row on this screen. Absent = not touched yet. */
interface RowOutcome {
  ok: boolean;
  reindexed: boolean;
  error?: string | null;
}

const outcomeTag = (o: RowOutcome) => {
  if (!o.ok) {
    return <Tag tag='danger'>{o.error || 'refused'}</Tag>;
  }
  if (!o.reindexed) {
    return <Tag tag='warning'>saved, not reindexed</Tag>;
  }
  return <Tag tag='success'>applied</Tag>;
};

const SeoTitleList: React.FC = () => {
  const [maxLength, setMaxLength] = React.useState(60);
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [drafts, setDrafts] = React.useState<Record<number, string>>({});
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [notice, setNotice] = React.useState<string | null>(null);
  const [outcomes, setOutcomes] = React.useState<Record<number, RowOutcome>>({});
  const [selected, setSelected] = React.useState<Set<number>>(new Set());
  const [confirming, setConfirming] = React.useState(false);
  const [batchLines, setBatchLines] = React.useState<{ lines: string[]; failed: number; notReindexed: number } | null>(null);

  const { data, isLoading, isFetching, isError } = useGetSeoTitlesQuery({ maxLength, page, limit });
  const [applyTitle, { isLoading: applying }] = useApplySeoTitleMutation();
  const [applyTitles, { isLoading: applyingBatch }] = useApplySeoTitlesMutation();
  const busy = applying || applyingBatch;

  const rows = data?.list ?? [];
  const pages = data && limit > 0 ? Math.ceil(data.total / limit) : 0;

  // A filter or page change invalidates both the page you were on and what you had ticked.
  React.useEffect(() => {
    setPage(1);
  }, [maxLength, limit]);
  React.useEffect(() => {
    setSelected(new Set());
    setConfirming(false);
  }, [maxLength, limit, page]);

  // Only rows on THIS page count — a ticked id whose row has since left the list is inert.
  const onPage = React.useMemo(() => new Set(rows.map(r => r.goodsId)), [rows]);
  const selectedRows = rows.filter(r => selected.has(r.goodsId) && !outcomes[r.goodsId]?.ok);
  const clean = cleanRowIds(rows, drafts).filter(id => !outcomes[id]?.ok);
  const allCleanSelected = clean.length > 0 && clean.every(id => selected.has(id));

  const toggleRow = (goodsId: number) =>
    setSelected(prev => {
      const next = new Set(prev);
      if (next.has(goodsId)) {
        next.delete(goodsId);
      } else {
        next.add(goodsId);
      }
      return next;
    });

  const toggleClean = () =>
    setSelected(prev => {
      const next = new Set([...prev].filter(id => onPage.has(id)));
      if (allCleanSelected) {
        clean.forEach(id => next.delete(id));
      } else {
        clean.forEach(id => next.add(id));
      }
      return next;
    });

  const onApply = async (row: ISeoTitleRow) => {
    setActionError(null);
    setNotice(null);
    const title = draftOf(row, drafts).trim();
    if (!title) {
      setActionError('Title must not be blank.');
      return;
    }
    const res = await applyTitle({ goodsId: row.goodsId, title });
    const msg = mutationError(res);
    if (msg) {
      setActionError(msg);
      return;
    }
    const body = 'data' in res ? res.data?.data : undefined;
    const warning = applyWarning(body);
    setOutcomes(prev => ({ ...prev, [row.goodsId]: { ok: true, reindexed: !warning } }));
    if (warning) {
      setNotice(`#${row.goodsId}: ${warning}`);
    }
  };

  const onApplySelected = async () => {
    setActionError(null);
    setNotice(null);
    setConfirming(false);
    const items = batchItems(rows, selected, drafts).filter(i => !outcomes[i.goodsId]?.ok);
    if (items.length === 0) {
      setActionError('Nothing to apply — the selected rows are blank or unchanged.');
      return;
    }
    if (items.length > SEO_BATCH_LIMIT) {
      setActionError(`At most ${SEO_BATCH_LIMIT} titles per batch.`);
      return;
    }
    const res = await applyTitles({ items });
    const msg = mutationError(res);
    if (msg) {
      setActionError(msg);
      return;
    }
    const body = 'data' in res ? res.data?.data : undefined;
    const summary = summarizeBatch(body);
    setBatchLines({ lines: summary.lines, failed: summary.failed, notReindexed: summary.notReindexed });
    setOutcomes(prev => {
      const next = { ...prev };
      (body?.results ?? []).forEach(r => {
        if (r.goodsId != null) {
          next[r.goodsId] = { ok: r.ok, reindexed: r.reindexed, error: r.error };
        }
      });
      return next;
    });
    // Rows that landed leave the list on refetch; rows that were refused stay ticked so the
    // administrator can fix the draft and try again.
    setSelected(prev => {
      const next = new Set(prev);
      (body?.results ?? []).forEach(r => {
        if (r.ok && r.goodsId != null) {
          next.delete(r.goodsId);
        }
      });
      return next;
    });
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <h4 className='mb-0 filter-item'>Long product titles</h4>
        <select
          className='form-select filter-item'
          style={{ width: 190 }}
          value={maxLength}
          onChange={e => setMaxLength(Number(e.target.value))}
          aria-label='Target title length'
        >
          {LENGTH_CHOICES.map(n => (
            <option key={n} value={n}>
              Longer than {n} chars
            </option>
          ))}
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 120 }}
          value={limit}
          onChange={e => setLimit(Number(e.target.value))}
          aria-label='Rows per page'
        >
          {PAGE_SIZES.map(n => (
            <option key={n} value={n}>
              {n} / page
            </option>
          ))}
        </select>
        {confirming ? (
          <span className='filter-item'>
            Rename {selectedRows.length} product{selectedRows.length === 1 ? '' : 's'} and reindex them for on-site search?{' '}
            <button className='btn btn-sm btn-danger' type='button' disabled={busy} onClick={onApplySelected}>
              Confirm
            </button>{' '}
            <button className='btn btn-sm btn-outline-secondary' type='button' onClick={() => setConfirming(false)}>
              Cancel
            </button>
          </span>
        ) : (
          <button
            className='btn btn-sm btn-primary filter-item'
            type='button'
            disabled={busy || selectedRows.length === 0}
            onClick={() => setConfirming(true)}
          >
            Apply selected ({selectedRows.length})
          </button>
        )}
        {(isFetching || busy) && <Spinner />}
      </div>

      <p className='text-muted small'>
        Google shows about the first {maxLength} characters of a result title — the rest is cut and a searcher never reads it.
        {data ? ` ${data.total} of ${data.scanned} live products are over.` : ''} The proposal shortens your own wording at a word
        boundary; it is not generated from keyword data. Edit any row before applying. Applying updates the product name and
        reindexes it for on-site search. The checkbox in the header selects only rows not marked <em>needs review</em>.
      </p>

      {isError && <div className='alert alert-danger'>Failed to load the title worklist.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}
      {notice && <div className='alert alert-warning'>{notice}</div>}
      {batchLines && (
        <div className={`alert ${batchLines.failed > 0 || batchLines.notReindexed > 0 ? 'alert-warning' : 'alert-success'}`} role='status'>
          <button type='button' className='btn-close float-end' aria-label='Dismiss' onClick={() => setBatchLines(null)} />
          {batchLines.lines.map((line, i) => (
            <div key={i}>{line}</div>
          ))}
        </div>
      )}

      <table className='el-table'>
        <thead>
          <tr>
            <th style={{ width: 36 }}>
              <input
                type='checkbox'
                aria-label='Select all rows on this page that do not need review'
                checked={allCleanSelected}
                disabled={clean.length === 0}
                onChange={toggleClean}
              />
            </th>
            <th style={{ width: 90 }}>Goods</th>
            <th style={{ width: 140 }}>Category</th>
            <th>Current title</th>
            <th>Proposed title (editable)</th>
            <th style={{ width: 190 }}>Search demand</th>
            <th className='text-end' style={{ width: 120 }}>
              Actions
            </th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={7}>
                <Spinner />
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td colSpan={7} className='text-muted'>
                No live product titles over {maxLength} characters.
              </td>
            </tr>
          ) : (
            rows.map(row => {
              const draft = draftOf(row, drafts);
              const outcome = outcomes[row.goodsId];
              const done = Boolean(outcome?.ok);
              return (
                <tr key={row.goodsId}>
                  <td>
                    <input
                      type='checkbox'
                      aria-label={`Select goods ${row.goodsId}`}
                      checked={selected.has(row.goodsId) && !done}
                      disabled={done}
                      onChange={() => toggleRow(row.goodsId)}
                    />
                  </td>
                  <td>
                    <a href={`/admin/goods/${row.goodsId}/edit`} target='_blank' rel='noreferrer'>
                      #{row.goodsId}
                    </a>
                  </td>
                  <td className='text-muted small'>{row.category || '—'}</td>
                  <td>
                    <div className='small'>{row.currentTitle}</div>
                    <Tag tag={lengthTag(row.currentLength, maxLength)}>{row.currentLength} chars</Tag>
                  </td>
                  <td>
                    <textarea
                      className='form-control form-control-sm'
                      rows={2}
                      value={draft}
                      disabled={done}
                      onChange={e => setDrafts(prev => ({ ...prev, [row.goodsId]: e.target.value }))}
                      aria-label={`Proposed title for goods ${row.goodsId}`}
                    />
                    <Tag tag={lengthTag(draft.trim().length, maxLength)}>{draft.trim().length} chars</Tag>{' '}
                    {row.needsReview && <Tag tag='warning'>needs review</Tag>}
                  </td>
                  <td className='small'>
                    {row.matchedKeyword ? (
                      <div>
                        <strong>{row.matchedKeyword}</strong>{' '}
                        {/* null = unknown, never 0 — an unpriced term is not a worthless one. */}
                        <span className='text-muted'>
                          {row.matchedKeywordVolume != null ? `${row.matchedKeywordVolume.toLocaleString()}/mo` : '—'}
                        </span>
                        {!row.keywordSurvives && <div className='text-warning'>dropped by the shortening</div>}
                      </div>
                    ) : (
                      <span className='text-muted'>no bought term matches this title</span>
                    )}
                    {row.terms.length > 0 && (
                      <details>
                        <summary className='text-muted'>category terms</summary>
                        <ul className='mb-0 ps-3'>
                          {row.terms.map(t => (
                            <li key={t.term}>
                              {t.term} <span className='text-muted'>{t.monthlySearches != null ? t.monthlySearches.toLocaleString() : '—'}</span>
                            </li>
                          ))}
                        </ul>
                      </details>
                    )}
                  </td>
                  <td className='text-end'>
                    {outcome && <div>{outcomeTag(outcome)}</div>}
                    {!done && (
                      <button
                        className='btn btn-sm btn-primary'
                        type='button'
                        disabled={busy || !isApplicable(row, draft)}
                        onClick={() => onApply(row)}
                      >
                        Apply
                      </button>
                    )}
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={data?.total} rowCount={rows.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default SeoTitleList;
