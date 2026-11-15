import type { ElTag } from 'app/views/adminViews/adminModule/_shared/crudUi';

// Seasonal candidacy admin (goods-management /insight/season-*): the pure half
// of the two panels, kept out of the components so it is testable without
// rendering (the promoFormat / seoTitleBatch pattern).
//
// The rule endpoint stores `terms`, `categoryIds` and `weights` as RAW JSON
// strings and validates nothing — the scorer parses them fail-soft, so a
// malformed edit silently degrades a season instead of being refused. These
// helpers are therefore the only gate: the form edits structured values and
// this file is the one place that turns them back into JSON.

/** The scorer clamps every weight into this range (SeasonWeights.clamp). */
export const WEIGHT_MIN = 1.0;
export const WEIGHT_MAX = 3.0;

export interface SeasonWeights {
  euMultiplier: number;
  freshnessBonusMax: number;
  categoryBoost: number;
  demandWeight: number;
}

/** SeasonWeights.DEFAULTS on the server — what a missing key resolves to. */
export const WEIGHT_DEFAULTS: SeasonWeights = { euMultiplier: 1.2, freshnessBonusMax: 1.25, categoryBoost: 1.15, demandWeight: 1.0 };

export const WEIGHT_FIELDS: { key: keyof SeasonWeights; label: string; hint: string }[] = [
  { key: 'euMultiplier', label: 'EU-warehouse multiplier', hint: 'Boost for products with measured German stock. A boost, never a filter.' },
  { key: 'freshnessBonusMax', label: 'Freshness bonus (max)', hint: 'Ceiling of the new-arrival bonus; 1.0 switches it off.' },
  { key: 'categoryBoost', label: 'Category boost', hint: 'Applied to products in the boosted categories below.' },
  { key: 'demandWeight', label: 'Demand weight', hint: 'How strongly views and sales lift the score.' },
];

export interface SeasonRuleLike {
  seasonKey: string;
  name?: string | null;
  windowStartMd?: string | null;
  windowEndMd?: string | null;
  terms?: string | null;
  categoryIds?: string | null;
  priceMin?: number | null;
  priceMax?: number | null;
  weights?: string | null;
  enabled?: boolean | null;
}

/** What the form edits: structured values, never JSON text. */
export interface SeasonRuleDraft {
  name: string;
  windowStartMd: string;
  windowEndMd: string;
  matchTerms: string[];
  excludeTerms: string[];
  categoryIds: number[];
  priceMin: string;
  priceMax: string;
  weights: SeasonWeights;
  enabled: boolean;
}

const parseArray = (json?: string | null): unknown[] => {
  if (!json) return [];
  try {
    const v = JSON.parse(json);
    return Array.isArray(v) ? v : [];
  } catch {
    return [];
  }
};

const cleanTerm = (t: unknown): string =>
  String(t ?? '')
    .trim()
    .toLowerCase();

/** One term list with `-` exclusions → two lists; blanks and duplicates dropped. */
export const splitTerms = (json?: string | null): { match: string[]; exclude: string[] } => {
  const match: string[] = [];
  const exclude: string[] = [];
  for (const raw of parseArray(json)) {
    const term = cleanTerm(raw);
    const isExclusion = term.startsWith('-');
    const bare = isExclusion ? term.replace(/^-+/, '').trim() : term;
    if (!bare) continue;
    const bucket = isExclusion ? exclude : match;
    if (!bucket.includes(bare)) bucket.push(bare);
  }
  return { match, exclude };
};

/** Two lists → the single stored array, match terms first. */
export const joinTerms = (match: string[], exclude: string[]): string => JSON.stringify([...match, ...exclude.map(t => `-${t}`)]);

/**
 * Normalise what an admin typed into a chip input. Commas split; a leading `-`
 * is stripped (the exclusion list adds its own prefix); an existing term is
 * not added twice.
 */
export const addTerms = (list: string[], typed: string): string[] => {
  const next = [...list];
  for (const piece of typed.split(',')) {
    const term = cleanTerm(piece).replace(/^-+/, '').trim();
    if (term && !next.includes(term)) next.push(term);
  }
  return next;
};

export const clampWeight = (v: unknown, fallback: number): number => {
  const n = typeof v === 'number' ? v : Number(v);
  if (v === null || v === undefined || v === '' || !Number.isFinite(n)) return fallback;
  return Math.max(WEIGHT_MIN, Math.min(WEIGHT_MAX, n));
};

/** Mirrors SeasonWeights.parse: anything missing or unusable becomes the default. */
export const parseWeights = (json?: string | null): SeasonWeights => {
  let obj: Record<string, unknown> = {};
  try {
    const v = json ? JSON.parse(json) : null;
    if (v && typeof v === 'object' && !Array.isArray(v)) obj = v as Record<string, unknown>;
  } catch {
    obj = {};
  }
  return {
    euMultiplier: clampWeight(obj.euMultiplier, WEIGHT_DEFAULTS.euMultiplier),
    freshnessBonusMax: clampWeight(obj.freshnessBonusMax, WEIGHT_DEFAULTS.freshnessBonusMax),
    categoryBoost: clampWeight(obj.categoryBoost, WEIGHT_DEFAULTS.categoryBoost),
    demandWeight: clampWeight(obj.demandWeight, WEIGHT_DEFAULTS.demandWeight),
  };
};

export const serializeWeights = (w: SeasonWeights): string =>
  JSON.stringify({
    euMultiplier: w.euMultiplier,
    freshnessBonusMax: w.freshnessBonusMax,
    categoryBoost: w.categoryBoost,
    demandWeight: w.demandWeight,
  });

export const parseCategoryIds = (json?: string | null): number[] => {
  const out: number[] = [];
  for (const raw of parseArray(json)) {
    const n = Number(raw);
    if (Number.isInteger(n) && n > 0 && !out.includes(n)) out.push(n);
  }
  return out;
};

const priceText = (v?: number | null): string => (v == null ? '' : String(v));

export const toDraft = (rule: SeasonRuleLike): SeasonRuleDraft => {
  const { match, exclude } = splitTerms(rule.terms);
  return {
    name: rule.name ?? '',
    windowStartMd: rule.windowStartMd ?? '',
    windowEndMd: rule.windowEndMd ?? '',
    matchTerms: match,
    excludeTerms: exclude,
    categoryIds: parseCategoryIds(rule.categoryIds),
    priceMin: priceText(rule.priceMin),
    priceMax: priceText(rule.priceMax),
    weights: parseWeights(rule.weights),
    enabled: rule.enabled !== false,
  };
};

const DAYS_IN_MONTH = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

/** MM-DD with a real month and a day that month can have (02-29 allowed). */
export const isMonthDay = (v: string): boolean => {
  const m = /^(\d{2})-(\d{2})$/.exec(v);
  if (!m) return false;
  const month = Number(m[1]);
  const day = Number(m[2]);
  return month >= 1 && month <= 12 && day >= 1 && day <= DAYS_IN_MONTH[month - 1];
};

const parsePrice = (text: string): number | null | 'invalid' => {
  const t = text.trim();
  if (!t) return null;
  const n = Number(t);
  return Number.isFinite(n) && n >= 0 ? n : 'invalid';
};

/** Field → message for everything the server would store without complaint. */
export const validateDraft = (d: SeasonRuleDraft): Record<string, string> => {
  const errors: Record<string, string> = {};
  if (!d.name.trim()) errors.name = 'Name is required.';
  else if (d.name.trim().length > 63) errors.name = 'Name is limited to 63 characters.';
  if (!isMonthDay(d.windowStartMd)) errors.windowStartMd = 'Use MM-DD, for example 09-01.';
  if (!isMonthDay(d.windowEndMd)) errors.windowEndMd = 'Use MM-DD, for example 11-30.';
  if (d.matchTerms.length === 0) errors.matchTerms = 'At least one match term is required — a season with none finds no products.';
  const overlap = d.matchTerms.filter(t => d.excludeTerms.includes(t));
  if (overlap.length > 0) errors.excludeTerms = `A term cannot be both matched and excluded: ${overlap.join(', ')}.`;
  const min = parsePrice(d.priceMin);
  const max = parsePrice(d.priceMax);
  if (min === 'invalid') errors.priceMin = 'Enter a price of 0 or more, or leave it empty.';
  if (max === 'invalid') errors.priceMax = 'Enter a price of 0 or more, or leave it empty.';
  if (typeof min === 'number' && typeof max === 'number' && min > max) errors.priceMax = 'The maximum price is below the minimum.';
  for (const f of WEIGHT_FIELDS) {
    const w = d.weights[f.key];
    if (!Number.isFinite(w) || w < WEIGHT_MIN || w > WEIGHT_MAX) errors[f.key] = `Must be between ${WEIGHT_MIN.toFixed(1)} and ${WEIGHT_MAX.toFixed(1)}.`;
  }
  return errors;
};

export interface SeasonRulePatch {
  name?: string;
  windowStartMd?: string;
  windowEndMd?: string;
  terms?: string;
  categoryIds?: string;
  priceMin?: number;
  priceMax?: number;
  weights?: string;
  enabled?: boolean;
}

const sameList = <T>(a: T[], b: T[]): boolean => a.length === b.length && a.every((v, i) => v === b[i]);

/**
 * Only the fields that changed (the endpoint treats an absent field as "leave
 * it alone"). `cannotClear` names price bounds the admin emptied: the patch
 * cannot express "set to null", so they are reported instead of silently kept.
 */
export const diffDraft = (rule: SeasonRuleLike, d: SeasonRuleDraft): { patch: SeasonRulePatch; cannotClear: string[] } => {
  const before = toDraft(rule);
  const patch: SeasonRulePatch = {};
  const cannotClear: string[] = [];
  if (d.name.trim() !== before.name) patch.name = d.name.trim();
  if (d.windowStartMd !== before.windowStartMd) patch.windowStartMd = d.windowStartMd;
  if (d.windowEndMd !== before.windowEndMd) patch.windowEndMd = d.windowEndMd;
  if (!sameList(d.matchTerms, before.matchTerms) || !sameList(d.excludeTerms, before.excludeTerms)) {
    patch.terms = joinTerms(d.matchTerms, d.excludeTerms);
  }
  if (!sameList(d.categoryIds, before.categoryIds)) patch.categoryIds = JSON.stringify(d.categoryIds);
  for (const key of ['priceMin', 'priceMax'] as const) {
    const now = parsePrice(d[key]);
    const was = parsePrice(before[key]);
    if (now === was || now === 'invalid') continue;
    if (now === null) cannotClear.push(key === 'priceMin' ? 'minimum price' : 'maximum price');
    else patch[key] = now;
  }
  if (WEIGHT_FIELDS.some(f => d.weights[f.key] !== before.weights[f.key])) patch.weights = serializeWeights(d.weights);
  if (d.enabled !== before.enabled) patch.enabled = d.enabled;
  return { patch, cannotClear };
};

export const isEmptyPatch = (p: SeasonRulePatch): boolean => Object.keys(p).length === 0;

// ---- candidates ------------------------------------------------------------

export const CANDIDATE_STATUSES: { value: string; label: string }[] = [
  { value: 'auto', label: 'Published' },
  { value: 'proposed', label: 'Proposed' },
  { value: 'dismissed', label: 'Dismissed' },
];

export const statusLabel = (status?: string | null): string => {
  const s = (status || '').toLowerCase();
  return CANDIDATE_STATUSES.find(x => x.value === s)?.label ?? (status || '—');
};

export const statusTag = (status?: string | null): ElTag => {
  const s = (status || '').toLowerCase();
  if (s === 'auto') return 'success';
  if (s === 'dismissed') return 'info';
  return 'primary';
};

export const tierTag = (tier?: string | null): ElTag => {
  const t = (tier || '').toLowerCase();
  if (t === 'hot') return 'danger';
  if (t === 'featured') return 'warning';
  return 'primary';
};

/** `reasons` is a JSON column the backend hands over as text; tolerate an array too. */
export const parseReasons = (reasons: unknown): string[] => {
  if (Array.isArray(reasons)) return reasons.map(r => String(r));
  if (typeof reasons !== 'string' || !reasons.trim()) return [];
  try {
    const v = JSON.parse(reasons);
    if (Array.isArray(v)) return v.map(r => String(r));
  } catch {
    // not JSON — show the text as it is
  }
  return [reasons];
};

/** A veto can be placed on anything not already vetoed; only a veto can be undone. */
export const canDismiss = (status?: string | null): boolean => (status || '').toLowerCase() !== 'dismissed';
export const canRestore = (status?: string | null): boolean => (status || '').toLowerCase() === 'dismissed';

export const pageOf = <T>(rows: T[], page: number, size: number): T[] => rows.slice((page - 1) * size, page * size);
export const pageCount = (total: number, size: number): number => Math.max(1, Math.ceil(total / size));

// ---- run summary -----------------------------------------------------------

export interface SeasonRunRow {
  seasonKey: string;
  scanned?: number | null;
  scored?: number | null;
  published?: number | null;
  droppedByCap?: number | null;
  rejections?: Record<string, number> | null;
  discovery?: { discardedRelaxed?: number | null; discardedOffTitle?: number | null; discardedExcluded?: number | null } | null;
  tierCuts?: { hot?: number | null; featured?: number | null } | null;
}

/** "uncosted 12, below floor 3" — largest first; "—" when nothing was rejected. */
export const fmtRejections = (rejections?: Record<string, number> | null): string => {
  const entries = Object.entries(rejections ?? {}).filter(([, n]) => Number(n) > 0);
  if (entries.length === 0) return '—';
  return entries
    .sort((a, b) => Number(b[1]) - Number(a[1]))
    .map(([reason, n]) => `${reason} ${n}`)
    .join(', ');
};

/** Tier cuts are null when nothing scored — never render that as 0. */
export const fmtCut = (v?: number | null): string => (v == null ? '—' : Number(v).toFixed(1));
