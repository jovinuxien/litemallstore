import { describe, expect, it } from '@jest/globals';

import {
  addTerms,
  canDismiss,
  canRestore,
  clampWeight,
  diffDraft,
  fmtCut,
  fmtRejections,
  isEmptyPatch,
  isMonthDay,
  joinTerms,
  pageCount,
  pageOf,
  parseCategoryIds,
  parseReasons,
  parseWeights,
  splitTerms,
  statusLabel,
  toDraft,
  validateDraft,
  WEIGHT_DEFAULTS,
} from './seasonFormat';

const RULE = {
  seasonKey: 'autumn',
  name: 'Autumn',
  windowStartMd: '09-01',
  windowEndMd: '11-30',
  terms: '["autumn","Blanket","-summer"," -cooling ","blanket",""]',
  categoryIds: '[1036143,"1036495",0,"x"]',
  priceMin: 5,
  priceMax: 500,
  weights: '{"euMultiplier":1.2,"freshnessBonusMax":1.25,"categoryBoost":1.15,"demandWeight":1}',
  enabled: true,
};

describe('terms', () => {
  it('splits one stored array into match and exclude lists, cleaned and de-duplicated', () => {
    expect(splitTerms(RULE.terms)).toEqual({ match: ['autumn', 'blanket'], exclude: ['summer', 'cooling'] });
  });

  it('treats malformed or non-array JSON as no terms instead of throwing', () => {
    expect(splitTerms('not json')).toEqual({ match: [], exclude: [] });
    expect(splitTerms('{"a":1}')).toEqual({ match: [], exclude: [] });
    expect(splitTerms(null)).toEqual({ match: [], exclude: [] });
  });

  it('joins back with the exclusion prefix and round-trips', () => {
    const json = joinTerms(['autumn', 'wool'], ['summer']);
    expect(json).toBe('["autumn","wool","-summer"]');
    expect(splitTerms(json)).toEqual({ match: ['autumn', 'wool'], exclude: ['summer'] });
  });

  it('adds typed terms: comma-split, lower-cased, prefix stripped, no duplicates', () => {
    expect(addTerms(['wool'], ' Halloween, -Xmas ,wool,, ')).toEqual(['wool', 'halloween', 'xmas']);
  });
});

describe('weights', () => {
  it('clamps into the scorer range and falls back on anything unusable', () => {
    expect(clampWeight(0, 1.2)).toBe(1);
    expect(clampWeight(9, 1.2)).toBe(3);
    expect(clampWeight('1.5', 1.2)).toBe(1.5);
    expect(clampWeight('abc', 1.2)).toBe(1.2);
    expect(clampWeight(null, 1.2)).toBe(1.2);
    expect(clampWeight('', 1.2)).toBe(1.2);
  });

  it('parses like the server: missing keys and broken JSON become defaults', () => {
    expect(parseWeights('{"euMultiplier":2}')).toEqual({ ...WEIGHT_DEFAULTS, euMultiplier: 2 });
    expect(parseWeights('{{')).toEqual(WEIGHT_DEFAULTS);
    expect(parseWeights('[1,2]')).toEqual(WEIGHT_DEFAULTS);
  });
});

describe('draft', () => {
  it('reads a rule into structured values', () => {
    const d = toDraft(RULE);
    expect(d.matchTerms).toEqual(['autumn', 'blanket']);
    expect(d.categoryIds).toEqual([1036143, 1036495]);
    expect(d.priceMin).toBe('5');
    expect(d.enabled).toBe(true);
    expect(parseCategoryIds('nope')).toEqual([]);
  });

  it('accepts real month-days only', () => {
    expect(isMonthDay('02-29')).toBe(true);
    expect(isMonthDay('02-30')).toBe(false);
    expect(isMonthDay('13-01')).toBe(false);
    expect(isMonthDay('9-1')).toBe(false);
  });

  it('passes a clean draft', () => {
    expect(validateDraft(toDraft(RULE))).toEqual({});
  });

  it('refuses what the server would store without complaint', () => {
    const d = toDraft(RULE);
    const errors = validateDraft({
      ...d,
      name: ' ',
      windowStartMd: '9/1',
      matchTerms: [],
      priceMin: '50',
      priceMax: '10',
      weights: { ...d.weights, demandWeight: 0 },
    });
    expect(Object.keys(errors).sort()).toEqual(['demandWeight', 'matchTerms', 'name', 'priceMax', 'windowStartMd']);
  });

  it('refuses a term that is both matched and excluded', () => {
    const d = toDraft(RULE);
    expect(validateDraft({ ...d, excludeTerms: ['autumn'] }).excludeTerms).toContain('autumn');
  });

  it('sends nothing when nothing changed', () => {
    const { patch, cannotClear } = diffDraft(RULE, toDraft(RULE));
    expect(isEmptyPatch(patch)).toBe(true);
    expect(cannotClear).toEqual([]);
  });

  it('sends only the changed fields, terms re-serialised with exclusions', () => {
    const d = toDraft(RULE);
    const { patch } = diffDraft(RULE, { ...d, matchTerms: [...d.matchTerms, 'wool'], enabled: false, priceMax: '250' });
    expect(patch).toEqual({ terms: '["autumn","blanket","wool","-summer","-cooling"]', enabled: false, priceMax: 250 });
  });

  it('sends well-formed weights JSON when a weight moves', () => {
    const d = toDraft(RULE);
    const { patch } = diffDraft(RULE, { ...d, weights: { ...d.weights, euMultiplier: 1.5 } });
    expect(JSON.parse(patch.weights as string)).toEqual({ euMultiplier: 1.5, freshnessBonusMax: 1.25, categoryBoost: 1.15, demandWeight: 1 });
    expect(Object.keys(patch)).toEqual(['weights']);
  });

  it('reports an emptied price bound instead of silently keeping it', () => {
    const { patch, cannotClear } = diffDraft(RULE, { ...toDraft(RULE), priceMax: '' });
    expect(isEmptyPatch(patch)).toBe(true);
    expect(cannotClear).toEqual(['maximum price']);
  });
});

describe('candidates', () => {
  it('labels auto as Published and offers the right decision per status', () => {
    expect(statusLabel('auto')).toBe('Published');
    expect(statusLabel('weird')).toBe('weird');
    expect(canDismiss('auto')).toBe(true);
    expect(canDismiss('proposed')).toBe(true);
    expect(canDismiss('dismissed')).toBe(false);
    expect(canRestore('dismissed')).toBe(true);
    expect(canRestore('auto')).toBe(false);
  });

  it('reads reasons from JSON text, an array, or plain text', () => {
    expect(parseReasons('["term: wool","EU stock"]')).toEqual(['term: wool', 'EU stock']);
    expect(parseReasons(['a'])).toEqual(['a']);
    expect(parseReasons('plain')).toEqual(['plain']);
    expect(parseReasons(null)).toEqual([]);
  });

  it('pages a long list', () => {
    const rows = Array.from({ length: 120 }, (_, i) => i);
    expect(pageCount(120, 50)).toBe(3);
    expect(pageCount(0, 50)).toBe(1);
    expect(pageOf(rows, 3, 50)).toEqual(rows.slice(100));
  });

  it('formats a run summary without inventing zeros', () => {
    expect(fmtRejections({ uncosted: 3, belowFloor: 12, none: 0 })).toBe('belowFloor 12, uncosted 3');
    expect(fmtRejections(null)).toBe('—');
    expect(fmtCut(null)).toBe('—');
    expect(fmtCut(773.3)).toBe('773.3');
  });
});
