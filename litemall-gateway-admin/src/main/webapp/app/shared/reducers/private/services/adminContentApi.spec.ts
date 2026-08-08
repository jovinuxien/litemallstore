import { describe, expect, it } from '@jest/globals';

import { IPageSummary, ListData, listWithDates, withDates } from './adminContentApi';

// goods-management's module-wide ObjectMapper writes LocalDateTime as numeric
// arrays [y, m, d, h, min, s] — these normalisers turn them back into the ISO
// strings the article/DIY-page views were built against. Regression for the
// /admin/mall/page crash: an array reaching fmtTime's `.replace` threw in
// render and tripped the app-level ErrorBoundary for the whole session.

describe('withDates', () => {
  it('converts array timestamps to ISO strings', () => {
    const row = withDates({
      id: 1,
      name: 'Default Home',
      position: 'home',
      status: 'draft',
      addTime: [2026, 7, 13, 13, 4, 58] as unknown as string,
      updateTime: [2026, 7, 13, 15, 49, 58] as unknown as string,
    } as IPageSummary);
    expect(row.addTime).toBe('2026-07-13T13:04:58');
    expect(row.updateTime).toBe('2026-07-13T15:49:58');
  });

  it('passes ISO strings through untouched', () => {
    const row = withDates({ id: 2, addTime: '2026-07-13T13:04:58', updateTime: '2026-07-13 15:49:58' });
    expect(row.addTime).toBe('2026-07-13T13:04:58');
    expect(row.updateTime).toBe('2026-07-13 15:49:58');
  });

  it('leaves absent timestamps undefined and keeps other fields', () => {
    const row = withDates({ id: 3, name: 'x' } as { id: number; name: string; addTime?: string });
    expect(row.addTime).toBeUndefined();
    expect(row.name).toBe('x');
  });

  it('never throws on junk shapes — renders fall back to a dash instead of crashing', () => {
    const row = withDates({ id: 4, addTime: { bogus: true } as unknown as string, updateTime: 42 as unknown as string });
    expect(row.addTime).toBeUndefined();
    expect(row.updateTime).toBeUndefined();
  });
});

describe('listWithDates', () => {
  it('normalises every row and keeps the total', () => {
    const data: ListData<IPageSummary> = {
      total: 2,
      list: [
        { id: 1, name: 'a', position: 'home', status: 'draft', updateTime: [2026, 7, 13, 15, 49, 58] as unknown as string },
        { id: 2, name: 'b', position: 'custom', status: 'active', updateTime: '2026-07-14T09:00:00' },
      ],
    };
    const out = listWithDates(data);
    expect(out.total).toBe(2);
    expect(out.list[0].updateTime).toBe('2026-07-13T15:49:58');
    expect(out.list[1].updateTime).toBe('2026-07-14T09:00:00');
  });

  it('yields an empty page when the envelope carried no data', () => {
    expect(listWithDates(undefined)).toEqual({ list: [], total: 0 });
  });
});
