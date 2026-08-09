import { attributionOf, brandIdOf } from './attribution';

describe('brandIdOf', () => {
  it('reads the value-object and plain-number forms', () => {
    expect(brandIdOf({ manufacturerId: { id: 7 } })).toBe(7);
    expect(brandIdOf({ manufacturerId: 12 })).toBe(12);
  });

  it('treats missing/zero/invalid as no brand', () => {
    expect(brandIdOf(null)).toBe(0);
    expect(brandIdOf({})).toBe(0);
    expect(brandIdOf({ manufacturerId: null })).toBe(0);
    expect(brandIdOf({ manufacturerId: { id: 0 } })).toBe(0);
    expect(brandIdOf({ manufacturerId: { id: undefined } })).toBe(0);
    expect(brandIdOf({ manufacturerId: -3 })).toBe(0);
  });
});

describe('attributionOf', () => {
  it('never renders a disabled row (the curation gate — raw supplier legal names)', () => {
    expect(attributionOf({ name: 'XIN BO EDUCATIONAL CONSULTATION PTE. LTD.', kind: 1, displayEnabled: 0 })).toBeNull();
    expect(attributionOf({ name: 'Acme', kind: 0, displayEnabled: false })).toBeNull();
  });

  it('labels enabled kind=1 as store and kind=0 as brand', () => {
    expect(attributionOf({ name: 'Sunrise Living', kind: 1, displayEnabled: 1 })).toEqual({ label: 'store', name: 'Sunrise Living' });
    expect(attributionOf({ name: 'Acme', kind: 0, displayEnabled: true })).toEqual({ label: 'brand', name: 'Acme' });
  });

  it('treats pre-V60 rows (no displayEnabled/kind fields) as enabled manual brands', () => {
    expect(attributionOf({ name: 'Legacy Seed' })).toEqual({ label: 'brand', name: 'Legacy Seed' });
  });

  it('renders nothing for missing rows or blank names', () => {
    expect(attributionOf(null)).toBeNull();
    expect(attributionOf(undefined)).toBeNull();
    expect(attributionOf({ name: '   ', kind: 1, displayEnabled: 1 })).toBeNull();
    expect(attributionOf({ kind: 0, displayEnabled: 1 })).toBeNull();
  });
});
