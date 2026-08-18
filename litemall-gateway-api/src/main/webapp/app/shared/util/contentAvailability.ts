import { catalogApi, contentApi, IBrand, ITopic } from 'app/shared/api';
import { attributionOf } from './attribution';

/**
 * Wave 26 — nav honesty on a narrowed storefront.
 *
 * Narrowing the catalogue to the anchor cluster left whole storefront sections
 * standing with nothing behind them: every one of the 49 seed brands reports
 * `goodsCount 0`, every seeded topic carries an empty goods list, the article
 * CMS is empty and no group-buy campaign is running. The header strip, the
 * "☰ All" drawer and the footer advertised all four regardless, so a shopper
 * clicking "Brands" or "Topics" landed on an empty page or a wall of tiles that
 * lead nowhere.
 *
 * This module is the one place that answers "does this surface have anything to
 * show?", so the nav entry and the page it points at can never disagree. It is
 * deliberately DATA-driven rather than a static removal: an admin who enables a
 * brand, publishes a topic or starts a campaign gets the entry back with no
 * rebuild.
 *
 * Failure semantics, in the same spirit as the backend's own empty-category nav
 * filter (which serves the unfiltered list rather than an empty nav when the
 * counts are unavailable):
 *   - probe still running  ⇒ NOT available: never advertise a section we have
 *     not yet confirmed has content.
 *   - probe FAILED         ⇒ available: a transient outage must not amputate
 *     the store's navigation.
 *   - cannot be judged (the payload carries no count at all) ⇒ available.
 *
 * Every probe runs at most once per browser session: in-flight promises are
 * shared, results are memoised in `sessionStorage` so a full page load does not
 * re-probe.
 */

export type ContentSurface = 'brands' | 'topics' | 'articles' | 'groupons';

export type BrandWithGoods = IBrand & { goodsCount: number };

/**
 * How many listed topics the NAV probe opens to decide whether the Topics entry
 * is worth showing. Topic rows carry no goods count (`/srv/topic/list` returns
 * title/subtitle/price/picUrl only), so substance can only be established by
 * reading each topic's detail — capped here so the decision costs a handful of
 * small GETs, not one per topic. Known limit: a substantial topic sorted below
 * this cap does not light the nav entry on its own, though /topics still lists
 * it. A `goodsCount` on the topic list row would collapse this to one request —
 * raised for goods-management, not worked around here.
 */
export const TOPIC_PROBE_LIMIT = 8;

/** Topics read by the /topics page itself, where paying per-topic is fine. */
export const TOPIC_PAGE_LIMIT = 20;

const CACHE_KEY = 'lm_content_avail';

/** Memoised probe results, by surface. */
const resolved: Partial<Record<ContentSurface, boolean>> = {};
/** In-flight probes, so concurrent callers share one request. */
const inFlight: Partial<Record<ContentSurface, Promise<boolean>>> = {};

const brandCache: { value?: Promise<BrandWithGoods[]> } = {};
const topicListCache: { value?: Promise<ITopic[]> } = {};
/** Per-topic substance, keyed by topic id — the nav's 8 are reused by the page. */
const topicGoods = new Map<number, Promise<boolean>>();

const readSession = (): Partial<Record<ContentSurface, boolean>> => {
  try {
    const raw = sessionStorage.getItem(CACHE_KEY);
    const parsed = raw ? JSON.parse(raw) : null;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
};

const writeSession = (surface: ContentSurface, available: boolean): void => {
  try {
    sessionStorage.setItem(CACHE_KEY, JSON.stringify({ ...readSession(), [surface]: available }));
  } catch {
    /* private mode / quota — the in-memory cache still holds for this page */
  }
};

/** A brand row proves itself with a positive count; an absent count cannot be judged. */
const brandCountOf = (b: IBrand): number | null => {
  const raw = (b as { goodsCount?: unknown }).goodsCount;
  return typeof raw === 'number' && Number.isFinite(raw) ? raw : null;
};

/** Fallback for a backend that predates the Wave-25 `goodsCount` on brand rows. */
const probeBrandCount = async (b: IBrand): Promise<number> => {
  if (b.id == null) return 0;
  try {
    const res = (await catalogApi.goodsList({ brandId: b.id, page: 1, limit: 1 })) as { total?: number; list?: unknown[] };
    return Number(res?.total ?? res?.list?.length ?? 0) || 0;
  } catch {
    return 0;
  }
};

/**
 * Brands that actually have on-sale goods behind them, most products first.
 *
 * `/srv/brand/list` has carried `goodsCount` since Wave 25, so this is normally
 * a single request; a row without one is counted the old way (one probe for
 * that row) rather than assumed empty. Rows that fail the Wave-25 curation gate
 * are never listed — a provider-captured supplier row carries a raw legal-entity
 * name until an admin renames and enables it.
 */
export const loadBrandsWithGoods = (): Promise<BrandWithGoods[]> => {
  if (!brandCache.value) {
    brandCache.value = contentApi
      .brandList({ page: 1, limit: 100 })
      .then(async res => {
        const listed = (res?.list ?? []).filter(b => attributionOf(b) !== null);
        const counts = await Promise.all(listed.map(async b => brandCountOf(b) ?? (await probeBrandCount(b))));
        return listed
          .map((b, i) => ({ ...b, goodsCount: counts[i] }))
          .filter(b => b.goodsCount > 0)
          .sort((a, b) => b.goodsCount - a.goodsCount);
      })
      .catch(err => {
        brandCache.value = undefined; // a failure must not be cached as "empty"
        throw err;
      });
  }
  return brandCache.value;
};

const listTopics = (limit: number): Promise<ITopic[]> => {
  if (!topicListCache.value) {
    topicListCache.value = contentApi
      .topicList({ page: 1, limit })
      .then(res => res?.list ?? [])
      .catch(err => {
        topicListCache.value = undefined;
        throw err;
      });
  }
  return topicListCache.value;
};

/** True when the topic points at at least one product. An unreadable topic is kept. */
const topicHasGoods = (id: number): Promise<boolean> => {
  const hit = topicGoods.get(id);
  if (hit) return hit;
  const probe = contentApi
    .topicDetail(id)
    .then(d => {
      const goods = (d?.goods ?? (d?.topic as { goods?: unknown[] } | undefined)?.goods ?? []) as unknown[];
      return goods.length > 0;
    })
    .catch(() => true);
  topicGoods.set(id, probe);
  return probe;
};

/**
 * Topics with products behind them, most-listed-first. Seeded topics whose
 * goods list is empty are dropped — they render as tiles that lead nowhere.
 */
export const loadTopicsWithGoods = (max: number = TOPIC_PAGE_LIMIT): Promise<ITopic[]> =>
  listTopics(Math.max(max, TOPIC_PAGE_LIMIT)).then(async list => {
    const considered = list.slice(0, max);
    const substance = await Promise.all(considered.map(t => (t.id == null ? Promise.resolve(false) : topicHasGoods(t.id))));
    return considered.filter((_, i) => substance[i]);
  });

const probe = (surface: ContentSurface): Promise<boolean> => {
  switch (surface) {
    case 'brands':
      return loadBrandsWithGoods().then(b => b.length > 0);
    case 'topics':
      return loadTopicsWithGoods(TOPIC_PROBE_LIMIT).then(t => t.length > 0);
    case 'articles':
      return contentApi.articleList(1, 1).then(res => (res?.total ?? 0) > 0);
    case 'groupons':
      return contentApi.grouponList({ page: 1, limit: 1 }).then(res => (res?.total ?? 0) > 0);
    default:
      return Promise.resolve(true);
  }
};

/**
 * Does this surface have anything to show? Resolves once per session; a failed
 * probe resolves `true` (fail-open — see the module note).
 */
export const surfaceAvailable = (surface: ContentSurface): Promise<boolean> => {
  if (resolved[surface] !== undefined) return Promise.resolve(resolved[surface] as boolean);
  const cached = readSession()[surface];
  if (typeof cached === 'boolean') {
    resolved[surface] = cached;
    return Promise.resolve(cached);
  }
  if (!inFlight[surface]) {
    inFlight[surface] = probe(surface)
      .catch(() => true)
      .then(available => {
        resolved[surface] = available;
        writeSession(surface, available);
        delete inFlight[surface];
        return available;
      });
  }
  return inFlight[surface] as Promise<boolean>;
};

/** Test seam — clears every memoised probe. */
export const __resetContentAvailability = (): void => {
  (Object.keys(resolved) as ContentSurface[]).forEach(k => delete resolved[k]);
  (Object.keys(inFlight) as ContentSurface[]).forEach(k => delete inFlight[k]);
  brandCache.value = undefined;
  topicListCache.value = undefined;
  topicGoods.clear();
  try {
    sessionStorage.removeItem(CACHE_KEY);
  } catch {
    /* nothing to clear */
  }
};
