/**
 * Should a filter section be rendered at all?
 *
 * The rail used to answer "yes, always" for the explicit facets and "yes, if
 * the backend named the field" for the dynamic ones — neither of which checks
 * that the facet has any VALUES. A group with zero buckets renders a heading
 * over nothing, which reads as a broken filter rather than an absent one.
 *
 * It stopped being hypothetical when goods-management started excluding
 * uncurated supplier names from the brand facet: the only display-enabled
 * brands with products are a handful of supplier rows, so after the reindex
 * `brand` can come back empty — and `<h3>Brand</h3>` would sit above nothing.
 * Whether the backend then omits the group or returns it with no entries is
 * not knowable from here, and does not matter: both are "nothing to show".
 *
 * The rule mirrors the nav availability probe rather than inventing a second
 * policy for the same question:
 *
 *   pending  -> hide   (never frame an answer we have not got)
 *   empty    -> hide   (the actual fix)
 *   failed   -> SHOW   (an outage must not amputate the filter rail)
 *   refined  -> SHOW   (always, or a ?brand= deep link cannot be cleared)
 */
export type FacetProbeStatus = 'pending' | 'ready' | 'failed';

export interface FacetGroupMeta {
  field: string;
  type: string;
  /** Buckets the probe saw. Absent on a backend that returns no entries array. */
  entryCount: number;
}

export interface FacetVisibilityInput {
  status: FacetProbeStatus;
  groups: FacetGroupMeta[];
  field: string;
  /** True when the user currently has a refinement on this attribute. */
  refined?: boolean;
}

export const shouldShowFacet = ({ status, groups, field, refined }: FacetVisibilityInput): boolean => {
  if (refined) return true;
  if (status === 'failed') return true;
  if (status === 'pending') return false;
  const group = groups.find(g => g.field === field);
  return group != null && group.entryCount > 0;
};

/**
 * Parses the `/srv/search` probe's `filters[]` into facet metadata.
 *
 * Interval groups (price, variant_price) are the exception to the entry-count
 * rule: their buckets are display labels the RangeInput never lists, so an
 * interval group counts as populated whenever the backend names it. Counting
 * its labels would work by accident today and break the price slider the day
 * the backend stops sending them.
 */
export const readFacetGroups = (data: unknown): FacetGroupMeta[] => {
  const d = (data ?? {}) as { filters?: unknown; facetGroups?: unknown };
  const raw = Array.isArray(d.filters) ? d.filters : Array.isArray(d.facetGroups) ? d.facetGroups : [];
  return (raw as Record<string, unknown>[])
    .map(g => {
      const field = String(g.field ?? g.fieldName ?? '');
      const type = String(g.type ?? 'term');
      const entries = Array.isArray(g.entries) ? g.entries : [];
      return { field, type, entryCount: type === 'interval' ? Math.max(1, entries.length) : entries.length };
    })
    .filter(g => g.field);
};
