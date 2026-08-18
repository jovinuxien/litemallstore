import { describe, expect, it } from '@jest/globals';

import { PAGE_CATEGORIES } from 'app/views/adminViews/adminModule/Page/pageFormat';
import { logSubject, pageCommand, pageSignature, pageSourceNotes } from './postizSource';

// Wave 20: helpers behind the Postiz DIY-page source (source picker, preview
// freshness signature, courtesy notes, history subject links).

describe('pageCommand', () => {
  it('builds the {pageId, channelIds[], startTime} body with a UTC ISO instant', () => {
    const cmd = pageCommand(7, new Set(['b', 'a']), '2026-08-10T09:00');
    expect(cmd.pageId).toBe(7);
    expect(cmd.channelIds).toEqual(expect.arrayContaining(['a', 'b']));
    expect(cmd.channelIds).toHaveLength(2);
    // the wire instant is UTC ISO and round-trips to the local input's epoch
    expect(cmd.startTime.endsWith('Z')).toBe(true);
    expect(new Date(cmd.startTime).getTime()).toBe(new Date('2026-08-10T09:00').getTime());
  });

  it('carries no interval — the page source is a single post', () => {
    const cmd = pageCommand(7, ['a'], '2026-08-10T09:00') as unknown as Record<string, unknown>;
    expect(cmd.intervalMinutes).toBeUndefined();
    expect(cmd.goodsIds).toBeUndefined();
  });
});

describe('pageSignature', () => {
  it('is stable under channel selection order', () => {
    expect(pageSignature(7, ['a', 'b'], '2026-08-10T09:00')).toBe(pageSignature(7, ['b', 'a'], '2026-08-10T09:00'));
  });

  it('changes when any input drifts (publish must re-preview)', () => {
    const base = pageSignature(7, ['a'], '2026-08-10T09:00');
    expect(pageSignature(8, ['a'], '2026-08-10T09:00')).not.toBe(base);
    expect(pageSignature(7, ['a', 'b'], '2026-08-10T09:00')).not.toBe(base);
    expect(pageSignature(7, ['a'], '2026-08-10T10:00')).not.toBe(base);
    expect(pageSignature(null, ['a'], '2026-08-10T09:00')).not.toBe(base);
  });
});

describe('pageSourceNotes', () => {
  it('is silent for an active general/coupon page', () => {
    expect(pageSourceNotes({ status: 'active', category: 'general' })).toEqual([]);
    expect(pageSourceNotes({ status: 'active', category: 'coupon' })).toEqual([]);
  });

  it('warns that only ACTIVE pages publish when a draft is picked', () => {
    const notes = pageSourceNotes({ status: 'draft', category: 'coupon' });
    expect(notes).toHaveLength(1);
    expect(notes[0]).toMatch(/ACTIVE/);
  });

  // Wave 21 retired errno 765 (the groupon-page hold) when priced group-buy
  // submit shipped, and the server now composes bespoke groupon post copy.
  // A note claiming a refusal would talk an admin out of a path that works.
  it('never warns about category — every category publishes since Wave 21', () => {
    for (const { value } of PAGE_CATEGORIES) {
      expect(pageSourceNotes({ status: 'active', category: value })).toEqual([]);
    }
  });

  it('warns only about the draft status on a draft groupon page, and stays empty with no selection', () => {
    const notes = pageSourceNotes({ status: 'draft', category: 'groupon' });
    expect(notes).toHaveLength(1);
    expect(notes[0]).toMatch(/ACTIVE/);
    expect(pageSourceNotes(null)).toEqual([]);
    expect(pageSourceNotes(undefined)).toEqual([]);
  });
});

describe('logSubject', () => {
  it('links page-source rows to the DIY page editor', () => {
    expect(logSubject({ pageId: 12 })).toEqual({ kind: 'page', to: '/admin/mall/page/12', label: 'Page #12' });
  });

  it('prefers pageId when a row somehow carries both', () => {
    expect(logSubject({ pageId: 12, goodsId: 10008302 })?.kind).toBe('page');
  });

  it('links goods rows to the insight view and yields null for neither', () => {
    expect(logSubject({ goodsId: 10008302 })).toEqual({ kind: 'goods', to: '/admin/goods/10008302/insight', label: 'Goods #10008302' });
    expect(logSubject({})).toBeNull();
  });
});
