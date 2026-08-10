import { describe, expect, it } from '@jest/globals';
import { isDisplayEnabled, isProviderRow, kindLabel, sourceLabel, toggledUpdateBody } from './brandFormat';

describe('sourceLabel', () => {
  it('treats manual, legacy local, empty and absent as Manual', () => {
    expect(sourceLabel('manual')).toBe('Manual');
    expect(sourceLabel('local')).toBe('Manual');
    expect(sourceLabel('')).toBe('Manual');
    expect(sourceLabel(undefined)).toBe('Manual');
    expect(sourceLabel(null)).toBe('Manual');
  });

  it('labels cj-supplier and passes unknown future sources through', () => {
    expect(sourceLabel('cj-supplier')).toBe('CJ supplier');
    expect(sourceLabel('CJ-Supplier')).toBe('CJ supplier');
    expect(sourceLabel('acme-brand-api')).toBe('acme-brand-api');
  });
});

describe('isProviderRow', () => {
  it('is false for manual/local/absent sources', () => {
    expect(isProviderRow({})).toBe(false);
    expect(isProviderRow({ source: 'manual' })).toBe(false);
    expect(isProviderRow({ source: 'local' })).toBe(false);
    expect(isProviderRow({ source: '' })).toBe(false);
  });

  it('is true for provider sources, including future ones', () => {
    expect(isProviderRow({ source: 'cj-supplier' })).toBe(true);
    expect(isProviderRow({ source: 'acme-brand-api' })).toBe(true);
  });
});

describe('kindLabel', () => {
  it('maps kind 1 to Store and everything else to Brand', () => {
    expect(kindLabel(1).label).toBe('Store');
    expect(kindLabel(0).label).toBe('Brand');
    expect(kindLabel(undefined).label).toBe('Brand');
    expect(kindLabel(null).label).toBe('Brand');
    expect(kindLabel(2).label).toBe('Brand');
  });
});

describe('isDisplayEnabled', () => {
  it('parses booleans and 0/1 tinyints', () => {
    expect(isDisplayEnabled(true)).toBe(true);
    expect(isDisplayEnabled(false)).toBe(false);
    expect(isDisplayEnabled(1)).toBe(true);
    expect(isDisplayEnabled(0)).toBe(false);
  });

  it('treats absent (pre-V60 rows) as visible', () => {
    expect(isDisplayEnabled(undefined)).toBe(true);
    expect(isDisplayEnabled(null)).toBe(true);
  });
});

describe('toggledUpdateBody', () => {
  const row = {
    id: 7,
    name: 'Wenling Chengdong Jiuwei Shoe and Hat Business',
    desc: '',
    source: 'cj-supplier',
    externalId: 'sup-123',
    kind: 1,
    displayEnabled: 0,
    goodsCount: 41,
  };

  it('flips only displayEnabled and keeps every other field', () => {
    const body = toggledUpdateBody(row);
    expect(body.displayEnabled).toBe(true);
    expect(body.id).toBe(7);
    expect(body.name).toBe(row.name);
    expect(body.source).toBe('cj-supplier');
    expect(body.externalId).toBe('sup-123');
    expect(body.kind).toBe(1);
  });

  it('never sends the read-only goodsCount back', () => {
    expect('goodsCount' in toggledUpdateBody(row)).toBe(false);
  });

  it('disables an enabled row, including pre-V60 rows with the field absent', () => {
    expect(toggledUpdateBody({ ...row, displayEnabled: true }).displayEnabled).toBe(false);
    expect(toggledUpdateBody({ id: 3, name: 'Legacy' }).displayEnabled).toBe(false);
  });
});
