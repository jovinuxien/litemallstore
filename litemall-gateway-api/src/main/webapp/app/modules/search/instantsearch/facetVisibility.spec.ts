import { readFacetGroups, shouldShowFacet } from './facetVisibility';

const groups = [
  { field: 'brand', type: 'term', entryCount: 0 },
  { field: 'Material', type: 'term', entryCount: 4 },
  { field: 'price', type: 'interval', entryCount: 1 },
];

describe('shouldShowFacet', () => {
  it('hides a facet the backend returned with no values', () => {
    // The case this exists for: goods-management now keeps uncurated supplier
    // names out of the brand facet, so `brand` can come back empty and the
    // heading would stand over an empty list.
    expect(shouldShowFacet({ status: 'ready', groups, field: 'brand' })).toBe(false);
  });

  it('shows a facet that has values', () => {
    expect(shouldShowFacet({ status: 'ready', groups, field: 'Material' })).toBe(true);
  });

  it('hides a facet the backend did not mention at all', () => {
    expect(shouldShowFacet({ status: 'ready', groups, field: 'Weight' })).toBe(false);
  });

  it('hides everything while the probe is still out — never frame an unknown answer', () => {
    expect(shouldShowFacet({ status: 'pending', groups: [], field: 'brand' })).toBe(false);
    expect(shouldShowFacet({ status: 'pending', groups, field: 'Material' })).toBe(false);
  });

  it('shows the facet when the probe FAILED — an outage must not amputate the rail', () => {
    expect(shouldShowFacet({ status: 'failed', groups: [], field: 'brand' })).toBe(true);
  });

  it('always shows a refined facet, or a deep link could not be cleared', () => {
    // /search?brand=Acme with an empty facet: hiding the section would strip
    // the only control that can remove the refinement.
    expect(shouldShowFacet({ status: 'ready', groups, field: 'brand', refined: true })).toBe(true);
    expect(shouldShowFacet({ status: 'pending', groups: [], field: 'brand', refined: true })).toBe(true);
  });
});

describe('readFacetGroups', () => {
  it('counts term buckets', () => {
    const parsed = readFacetGroups({
      filters: [{ field: 'brand', type: 'term', entries: [{ value: 'Acme', count: 3 }] }],
    });
    expect(parsed).toEqual([{ field: 'brand', type: 'term', entryCount: 1 }]);
  });

  it('reports an empty term group as empty rather than dropping it', () => {
    // Dropping it would look identical to "not mentioned" — true today, but it
    // would hide a real backend change behind a shrug.
    expect(readFacetGroups({ filters: [{ field: 'brand', type: 'term', entries: [] }] })).toEqual([
      { field: 'brand', type: 'term', entryCount: 0 },
    ]);
  });

  it('treats an interval group as populated even with no listed buckets', () => {
    // RangeInput never lists them — its bounds come from facets_stats — so
    // counting labels would kill the price slider the day the backend stops
    // sending display buckets.
    expect(readFacetGroups({ filters: [{ field: 'price', type: 'interval', entries: [] }] })).toEqual([
      { field: 'price', type: 'interval', entryCount: 1 },
    ]);
  });

  it('tolerates the fieldName spelling and drops nameless groups', () => {
    expect(readFacetGroups({ facetGroups: [{ fieldName: 'Weight', entries: [{ value: '1kg' }] }, { entries: [] }] })).toEqual([
      { field: 'Weight', type: 'term', entryCount: 1 },
    ]);
  });

  it('survives a shape it has never seen', () => {
    expect(readFacetGroups(null)).toEqual([]);
    expect(readFacetGroups({})).toEqual([]);
    expect(readFacetGroups({ filters: 'nope' })).toEqual([]);
  });
});
