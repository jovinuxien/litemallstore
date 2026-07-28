/**
 * Wave-13 slug contract — MUST stay byte-identical to the Java implementations
 * (edge `web/seo/Slugs.java`, goods-management sitemap builder): lowercase,
 * ASCII-fold (NFKD + combining marks stripped), runs of non-alphanumerics
 * collapse to a single `-`, dashes trimmed, capped at 80 chars.
 *
 * The hrefs built here are what the canonical tag and the sitemap also emit;
 * if the three drift, crawlers see competing "canonical" URLs for one product.
 */
const MAX_SLUG_LENGTH = 80;

export const slugify = (name?: string | null): string => {
  if (!name) return '';
  const folded = name.normalize('NFKD').replace(/\p{M}+/gu, '');
  const dashed = folded.toLowerCase().replace(/[^a-z0-9]+/g, '-');
  let trimmed = dashed.replace(/^-+/, '').replace(/-+$/, '');
  if (trimmed.length > MAX_SLUG_LENGTH) {
    trimmed = trimmed.slice(0, MAX_SLUG_LENGTH).replace(/-+$/, '');
  }
  return trimmed;
};

/**
 * Canonical product path: `/product/<id>-<slug>` for numeric goods ids with a
 * sluggable name; bare `/product/<id>` otherwise. Legacy `cj_<pid>` ids never
 * get a slug — their id is the OCS document key and must ride verbatim.
 */
export const productPath = (id: string | number, name?: string | null): string => {
  const idStr = String(id);
  if (!/^\d+$/.test(idStr)) return `/product/${idStr}`;
  const slug = slugify(name);
  return slug ? `/product/${idStr}-${slug}` : `/product/${idStr}`;
};

/**
 * Inverse for the `product/:id` route param: `<digits>-<slug>` → the digits;
 * anything else (bare numeric ids, legacy `cj_<pid>` keys) passes verbatim —
 * bare-id URLs stay valid forever, and the backend keys on this id as-is.
 */
export const goodsIdFromRoute = (segment?: string): string | undefined => {
  if (!segment) return segment;
  const match = /^(\d+)-.*$/.exec(segment);
  return match ? match[1] : segment;
};
