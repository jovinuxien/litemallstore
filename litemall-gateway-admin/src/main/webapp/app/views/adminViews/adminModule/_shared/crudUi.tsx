import * as React from 'react';

// Small presentational helpers shared by the admin CRUD list/form views, styled
// to the same upstream litemall-admin (Element) look as AdminGoodsList: an
// .el-table inside an .app-container, .el-tag pills, and an .el-pagination
// footer. Kept intentionally tiny — each entity view owns its own columns/form.

export type ElTag = 'success' | 'danger' | 'warning' | 'info' | 'primary';

export const Tag: React.FC<{ tag: ElTag; children: React.ReactNode }> = ({ tag, children }) => (
  <span className={`el-tag el-tag--${tag}`}>{children}</span>
);

export const Spinner: React.FC = () => <span className='spinner-border spinner-border-sm text-primary filter-item' role='status' />;

// Standard page-size selector values.
export const PAGE_SIZES = [10, 20, 50];

interface PaginationProps {
  page: number;
  /** Omit both when the backend returns a bare list with no page metadata
   *  (e.g. the promotion admin surface); the pager then falls back to the
   *  rowCount < limit "has more" heuristic and shows only the page number. */
  pages?: number;
  total?: number;
  rowCount: number;
  limit: number;
  busy?: boolean;
  onPage: (next: number) => void;
}

// Prev/Next pager with a total/“page N of M” summary, matching AdminGoodsList.
export const Pagination: React.FC<PaginationProps> = ({ page, pages = 0, total, rowCount, limit, busy, onPage }) => {
  const prevDisabled = page <= 1 || busy;
  const nextDisabled = (pages > 0 && page >= pages) || rowCount < limit || busy;
  return (
    <div className='el-pagination'>
      <span className='el-pagination-total'>
        {total != null ? `${total} item${total === 1 ? '' : 's'}` : `page ${page}`}
        {pages > 0 && ` · page ${page} of ${pages}`}
      </span>
      <button className='el-pager-btn' disabled={prevDisabled} onClick={() => onPage(page - 1)}>
        ‹ Prev
      </button>
      <button className='el-pager-btn' disabled={nextDisabled} onClick={() => onPage(page + 1)}>
        Next ›
      </button>
    </div>
  );
};

// The litemall envelope is HTTP 200 even on a business error (errno !== 0).
// Normalise an RTK-Query mutation result (or thrown error) into a message, or
// null on success.
export const errnoMessage = (res: unknown): string | null => {
  // RTK-Query mutation `.unwrap()` rejects with a FetchBaseQueryError on
  // transport failure; on success it resolves to our envelope.
  const env = res as { errno?: number; errmsg?: string } | undefined;
  if (env && typeof env.errno === 'number') {
    return env.errno === 0 ? null : env.errmsg || `Request failed (errno ${env.errno})`;
  }
  return 'Request failed.';
};
