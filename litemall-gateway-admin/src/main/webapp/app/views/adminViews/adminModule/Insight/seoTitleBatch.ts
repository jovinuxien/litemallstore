import type { ISeoBatchItem, ISeoBatchResult, ISeoTitleRow } from 'app/shared/reducers/private/services/adminSeoApi';

/**
 * Selection and result logic for the SEO title worklist's bulk apply, kept out of the component
 * so a test can reach it (the folder's promoFormat.ts / applyResult.ts convention).
 *
 * The one rule that matters: "select all" means "select the rows a human does NOT need to read
 * first" — `needsReview === false` and a draft that would actually change something. A row the
 * server flagged (truncation ate the keyword or the meaning) can still be ticked by hand after
 * editing; it is never swept up by the header checkbox.
 */

export type Drafts = Record<number, string>;

/** What the textarea holds for this row: the administrator's edit, else the server's proposal. */
export const draftOf = (row: ISeoTitleRow, drafts: Drafts): string => drafts[row.goodsId] ?? row.proposedTitle;

/** Non-blank and different from what is live — otherwise applying is a no-op or a refusal. */
export const isApplicable = (row: ISeoTitleRow, draft: string): boolean => {
  const t = draft.trim();
  return t.length > 0 && t !== row.currentTitle;
};

/** Rows the header checkbox may select: unflagged AND applicable. Order preserved. */
export const cleanRowIds = (rows: ISeoTitleRow[], drafts: Drafts): number[] =>
  rows.filter(r => !r.needsReview && isApplicable(r, draftOf(r, drafts))).map(r => r.goodsId);

/** The batch body for the ticked rows on this page, skipping anything that became blank. */
export const batchItems = (rows: ISeoTitleRow[], selected: ReadonlySet<number>, drafts: Drafts): ISeoBatchItem[] =>
  rows
    .filter(r => selected.has(r.goodsId))
    .map(r => ({ goodsId: r.goodsId, title: draftOf(r, drafts).trim() }))
    .filter(i => i.title.length > 0);

export interface BatchSummary {
  applied: number;
  failed: number;
  notReindexed: number;
  /** Human lines: one headline, then one per problem row with the server's wording verbatim. */
  lines: string[];
}

/** Turns the server's per-row report into what the banner says. Never hides a failed row. */
export const summarizeBatch = (data: ISeoBatchResult | undefined | null): BatchSummary => {
  const results = data?.results ?? [];
  const applied = results.filter(r => r.ok).length;
  const failed = results.filter(r => !r.ok).length;
  const notReindexed = results.filter(r => r.ok && !r.reindexed).length;
  const lines: string[] = [`Applied ${applied} of ${results.length} titles.`];
  results
    .filter(r => !r.ok)
    .forEach(r => lines.push(`#${r.goodsId ?? '?'}: ${r.error || 'refused'}`));
  results
    .filter(r => r.ok && !r.reindexed)
    .forEach(r => lines.push(`#${r.goodsId ?? '?'}: saved, but on-site search still shows the old title${r.error ? ` (${r.error})` : ''}`));
  return { applied, failed, notReindexed, lines };
};

/**
 * The warning to show after a SUCCESSFUL single apply, or null. The server answers errno 0 with
 * `reindexed:false` when MySQL took the title but the OCS document did not refresh — that is a
 * success with a caveat, not a failure to retry, and the caveat must not be swallowed.
 */
export const applyWarning = (data: { reindexed?: boolean; warning?: string } | undefined | null): string | null => {
  if (!data || data.reindexed !== false) {
    return null;
  }
  return data.warning || 'Saved, but on-site search still shows the old title.';
};
