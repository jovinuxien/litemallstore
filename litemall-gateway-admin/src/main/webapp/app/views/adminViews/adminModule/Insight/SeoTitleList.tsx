import {
  ISeoTitleRow,
  useApplySeoTitleMutation,
  useGetSeoTitlesQuery,
} from 'app/shared/reducers/private/services/adminSeoApi';
import { mutationError } from './applyResult';
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

const LENGTH_CHOICES = [50, 60, 70];

/** Google's cut point. Amber before it, red past it. */
const lengthTag = (len: number, max: number) => (len > max ? 'danger' : len > max - 10 ? 'warning' : 'success');

const SeoTitleList: React.FC = () => {
  const [maxLength, setMaxLength] = React.useState(60);
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [drafts, setDrafts] = React.useState<Record<number, string>>({});
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [applied, setApplied] = React.useState<Record<number, boolean>>({});

  const { data, isLoading, isFetching, isError } = useGetSeoTitlesQuery({ maxLength, page, limit });
  const [applyTitle, { isLoading: applying }] = useApplySeoTitleMutation();

  const rows = data?.list ?? [];
  const pages = data && limit > 0 ? Math.ceil(data.total / limit) : 0;

  // A filter change invalidates the page you were on.
  React.useEffect(() => {
    setPage(1);
  }, [maxLength, limit]);

  const draftFor = (row: ISeoTitleRow) => drafts[row.goodsId] ?? row.proposedTitle;

  const onApply = async (row: ISeoTitleRow) => {
    setActionError(null);
    const title = draftFor(row).trim();
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
    setApplied(prev => ({ ...prev, [row.goodsId]: true }));
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
        {isFetching && <Spinner />}
      </div>

      <p className='text-muted small'>
        Google shows about the first {maxLength} characters of a result title — the rest is cut and a searcher never reads it.
        {data ? ` ${data.total} of ${data.scanned} live products are over.` : ''} The proposal shortens your own wording at a word
        boundary; it is not generated from keyword data. Edit any row before applying. Applying updates the product name and
        reindexes it for on-site search.
      </p>

      {isError && <div className='alert alert-danger'>Failed to load the title worklist.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
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
              <td colSpan={6}>
                <Spinner />
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td colSpan={6} className='text-muted'>
                No live product titles over {maxLength} characters.
              </td>
            </tr>
          ) : (
            rows.map(row => {
              const draft = draftFor(row);
              const done = applied[row.goodsId];
              return (
                <tr key={row.goodsId}>
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
                    {done ? (
                      <Tag tag='success'>applied</Tag>
                    ) : (
                      <button
                        className='btn btn-sm btn-primary'
                        type='button'
                        disabled={applying || draft.trim() === row.currentTitle}
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
