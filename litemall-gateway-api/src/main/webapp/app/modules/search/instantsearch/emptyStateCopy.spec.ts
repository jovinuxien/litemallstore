import { emptyStateCopy, hasRefinements } from './emptyStateCopy';

/**
 * Wave 26 — the zero-results page is allowed to blame the department only when
 * nothing else explains the emptiness. Getting that wrong in either direction
 * is a lie to the shopper: "check the spelling" on a URL with no query, or
 * "we no longer stock this" on a live department a price filter just emptied.
 */
describe('empty-state copy', () => {
  it('blames the department on a bare /category/:id with nothing in it', () => {
    const copy = emptyStateCopy({ categoryId: '1005000' });
    expect(copy.title).toBe('Nothing in this department right now');
    expect(copy.body).toContain('home, garden and tools');
  });

  it('never mentions spelling when there was no search term', () => {
    expect(emptyStateCopy({ categoryId: '1005000' }).body).not.toMatch(/spelling/i);
  });

  it('blames the filters, not the department, when a refinement is active', () => {
    const copy = emptyStateCopy({ categoryId: '1036143', refined: true });
    expect(copy.title).toBe('No products match these filters');
  });

  it('names the query that found nothing, category page or not', () => {
    expect(emptyStateCopy({ query: 'trombone' }).title).toBe('No results for “trombone”');
    expect(emptyStateCopy({ categoryId: '1036143', query: 'trombone' }).title).toBe('No results for “trombone”');
  });

  it('falls back to the plain message on /search with neither query nor scope', () => {
    expect(emptyStateCopy({}).title).toBe('No products found');
  });

  it('promises no restock and no delivery window', () => {
    const copy = emptyStateCopy({ categoryId: '1005000' });
    expect(`${copy.title} ${copy.body}`).not.toMatch(/back soon|coming back|restock|\d+\s*(day|days|week)/i);
  });
});

describe('refinement detection', () => {
  it('sees a facet, a range and a toggle', () => {
    expect(hasRefinements({ refinementList: { brand: ['Acme'] } })).toBe(true);
    expect(hasRefinements({ range: { retail_price: '10:50' } })).toBe(true);
    expect(hasRefinements({ toggle: { deal_flag: true } })).toBe(true);
  });

  it('does not count a query, a page or a sort as a refinement', () => {
    expect(hasRefinements({ query: 'lamp', page: 3, sortBy: 'price_asc' })).toBe(false);
  });

  it('does not count emptied refinement containers', () => {
    expect(hasRefinements({ refinementList: {}, range: {} })).toBe(false);
    expect(hasRefinements({ refinementList: { brand: [] } })).toBe(false);
    expect(hasRefinements(null)).toBe(false);
    expect(hasRefinements(undefined)).toBe(false);
  });
});
