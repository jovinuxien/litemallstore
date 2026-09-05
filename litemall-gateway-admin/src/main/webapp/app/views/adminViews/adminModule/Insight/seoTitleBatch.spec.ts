import { describe, expect, it } from '@jest/globals';

import type { ISeoTitleRow } from 'app/shared/reducers/private/services/adminSeoApi';
import { BLANK_TITLE_ERROR, applyWarning, cleanRowIds, isApplicable, partitionBatch, summarizeBatch } from './seoTitleBatch';

const row = (over: Partial<ISeoTitleRow> = {}): ISeoTitleRow => ({
  goodsId: 1,
  category: 'Garden Tools',
  currentTitle: 'A long supplier title that runs well past what Google will show a searcher',
  currentLength: 74,
  proposedTitle: 'A long supplier title that runs well past what Google',
  proposedLength: 53,
  needsReview: false,
  matchedKeyword: null,
  matchedKeywordVolume: null,
  keywordSurvives: false,
  terms: [],
  ...over,
});

describe('cleanRowIds — what the header checkbox may sweep up', () => {
  it('selects unflagged rows whose draft would change the title', () => {
    const rows = [row({ goodsId: 1 }), row({ goodsId: 2 })];
    expect(cleanRowIds(rows, {})).toEqual([1, 2]);
  });

  it('never selects a needsReview row, even with a good-looking proposal', () => {
    const rows = [row({ goodsId: 1, needsReview: true }), row({ goodsId: 2 })];
    expect(cleanRowIds(rows, {})).toEqual([2]);
  });

  it('skips rows whose proposal equals the current title (server kept the original)', () => {
    const kept = row({ goodsId: 3, proposedTitle: row().currentTitle });
    expect(cleanRowIds([kept], {})).toEqual([]);
  });

  it('honours the administrator draft over the proposal', () => {
    const rows = [row({ goodsId: 1 })];
    expect(cleanRowIds(rows, { 1: '   ' })).toEqual([]);
    expect(cleanRowIds(rows, { 1: 'Edited title' })).toEqual([1]);
  });
});

describe('isApplicable', () => {
  it('rejects blank and unchanged drafts', () => {
    expect(isApplicable(row(), '')).toBe(false);
    expect(isApplicable(row(), '  \n ')).toBe(false);
    expect(isApplicable(row(), row().currentTitle)).toBe(false);
    expect(isApplicable(row(), 'Shorter')).toBe(true);
  });
});

describe('partitionBatch — a blank draft is refused, never silently dropped', () => {
  it('sends the trimmed draft for ticked rows only, in row order, and lists the blank ones', () => {
    const rows = [row({ goodsId: 1 }), row({ goodsId: 2 }), row({ goodsId: 3 })];
    const p = partitionBatch(rows, new Set([3, 1, 2]), { 1: '  Edited one  ', 2: '   ' });
    expect(p.items).toEqual([
      { goodsId: 1, title: 'Edited one' },
      { goodsId: 3, title: row().proposedTitle },
    ]);
    expect(p.blank).toEqual([2]);
  });

  it('accounts for every ticked row: items plus blank equals the selection on this page', () => {
    const rows = [row({ goodsId: 1 }), row({ goodsId: 2 })];
    const p = partitionBatch(rows, new Set([1, 2, 99]), { 2: '' });
    expect(p.items.length + p.blank.length).toBe(2);
  });
});

describe('summarizeBatch — the banner never hides a failed row', () => {
  it('counts and lists refusals and stale-index rows with the server wording verbatim', () => {
    const s = summarizeBatch({
      applied: 3,
      failed: 1,
      results: [
        { goodsId: 1, ok: true, title: 'A', changed: true, reindexed: true, error: null },
        { goodsId: 2, ok: false, title: null, changed: false, reindexed: false, error: 'no such goods: 2' },
        { goodsId: 3, ok: true, title: 'C', changed: true, reindexed: false, error: 'indexer down' },
        { goodsId: 4, ok: true, title: 'D', changed: false, reindexed: true, error: null },
      ],
    });
    expect(s.applied).toBe(3);
    expect(s.failed).toBe(1);
    expect(s.notReindexed).toBe(1);
    expect(s.lines).toEqual([
      'Applied 3 of 4 titles.',
      '#2: no such goods: 2',
      '#3: saved, but on-site search still shows the old title (indexer down)',
    ]);
  });

  it('copes with a missing body', () => {
    expect(summarizeBatch(undefined).lines).toEqual(['Applied 0 of 0 titles.']);
  });

  it('counts locally refused blank rows in the headline and lists them with the server wording', () => {
    const s = summarizeBatch(
      { applied: 1, failed: 0, results: [{ goodsId: 1, ok: true, title: 'A', changed: true, reindexed: true, error: null }] },
      [7],
    );
    expect(s.applied).toBe(1);
    expect(s.failed).toBe(1);
    expect(s.lines).toEqual(['Applied 1 of 2 titles.', `#7: ${BLANK_TITLE_ERROR}`]);
  });

  it('reports a blank-only batch without a server body', () => {
    expect(summarizeBatch(undefined, [4]).lines).toEqual(['Applied 0 of 1 titles.', `#4: ${BLANK_TITLE_ERROR}`]);
  });
});

describe('applyWarning — a stale index is a caveat on success, not a failure', () => {
  it('is silent when reindexed', () => {
    expect(applyWarning({ reindexed: true })).toBeNull();
    expect(applyWarning(undefined)).toBeNull();
  });

  it('shows the server warning verbatim, with a fallback', () => {
    expect(applyWarning({ reindexed: false, warning: 'Saved, but on-site search still shows the old title: indexer down' })).toBe(
      'Saved, but on-site search still shows the old title: indexer down',
    );
    expect(applyWarning({ reindexed: false })).toBe('Saved, but on-site search still shows the old title.');
  });
});
