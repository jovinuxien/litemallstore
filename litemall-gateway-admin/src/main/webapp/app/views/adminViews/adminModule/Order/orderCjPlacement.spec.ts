import { describe, expect, it } from '@jest/globals';

import { cjPlacementState } from 'app/shared/model/admin/order.model';
import {
  orderOpMessage,
  pendingItemsSummary,
  toApprovalStamp,
  toPendingCjPage,
} from 'app/shared/reducers/private/services/adminOrderCjApi';

// Wave 23: admin-gated CJ placement — pending-page normalisation, the
// approval stamp, the items summary, and the placement lifecycle predicate.

describe('toPendingCjPage', () => {
  it('normalises the standard page envelope, incl. array LocalDateTimes and object items', () => {
    const page = toPendingCjPage({
      errno: 0,
      data: {
        list: [
          {
            orderId: 7,
            orderSn: '2026080712345',
            addTime: [2026, 8, 7, 9, 5, 3],
            payTime: '2026-08-07T09:10:00',
            actualPrice: '38.38',
            consignee: 'Jane Doe',
            country: 'US',
            items: [{ goodsName: 'Spatula set', number: 2 }, 'Loose string item'],
            cjReady: true,
            holdReason: 'awaiting admin approval',
          },
        ],
        total: 3,
        pages: 1,
      },
    });
    expect(page.errmsg).toBeUndefined();
    expect(page.total).toBe(3);
    expect(page.pages).toBe(1);
    expect(page.list).toEqual([
      {
        orderId: 7,
        orderSn: '2026080712345',
        addTime: '2026-08-07T09:05:03',
        payTime: '2026-08-07T09:10:00',
        actualPrice: '38.38',
        consignee: 'Jane Doe',
        country: 'US',
        items: [{ name: 'Spatula set', number: 2 }, { name: 'Loose string item', number: undefined }],
        cjReady: true,
        holdReason: 'awaiting admin approval',
      },
    ]);
  });

  it('keeps a 2xx errno!==0 body honest via errmsg (never a silent empty page)', () => {
    const page = toPendingCjPage({ errno: 660, errmsg: 'manual placement mode is disabled' });
    expect(page.list).toEqual([]);
    expect(page.errmsg).toBe('manual placement mode is disabled');
    expect(toPendingCjPage({ errno: 661 }).errmsg).toBe('Request failed (errno 661)');
  });

  it('tolerates a bare list without envelope and derives totals', () => {
    const page = toPendingCjPage({ list: [{ id: 10, orderSn: 'sn-10' }] } as never);
    expect(page.list[0]).toMatchObject({ orderId: 10, orderSn: 'sn-10', items: [] });
    expect(page.total).toBe(1);
    expect(page.pages).toBe(1);
    expect(toPendingCjPage(undefined as never)).toEqual({ list: [], total: 0, pages: 0 });
  });
});

describe('pendingItemsSummary', () => {
  it('renders name ×qty and truncates past the cap', () => {
    expect(pendingItemsSummary(undefined)).toBe('—');
    expect(pendingItemsSummary([])).toBe('—');
    expect(pendingItemsSummary([{ name: 'A', number: 2 }, { name: 'B' }])).toBe('A ×2, B');
    expect(
      pendingItemsSummary([{ name: 'A', number: 1 }, { name: 'B', number: 1 }, { name: 'C', number: 1 }, { name: 'D' }, { name: 'E' }]),
    ).toBe('A ×1, B ×1, C ×1 +2 more');
  });
});

describe('toApprovalStamp', () => {
  it('reads both stamp spellings and normalises array datetimes', () => {
    expect(toApprovalStamp({ data: { errno: 0, data: { approvedBy: 'admin123', approvedTime: '2026-08-08T10:00:00' } } })).toEqual({
      approvedBy: 'admin123',
      approvedTime: '2026-08-08T10:00:00',
    });
    expect(
      toApprovalStamp({ data: { errno: 0, data: { cjPlacementApprovedBy: 'admin123', cjPlacementApprovedTime: [2026, 8, 8, 10, 0, 0] } } }),
    ).toEqual({ approvedBy: 'admin123', approvedTime: '2026-08-08T10:00:00' });
  });

  it('returns an empty stamp on refusals, empty payloads, and RTK errors', () => {
    expect(toApprovalStamp({ data: { errno: 662, errmsg: 'already placed' } })).toEqual({});
    expect(toApprovalStamp({ data: { errno: 0 } })).toEqual({ approvedBy: undefined, approvedTime: undefined });
    expect(toApprovalStamp({ error: { status: 502 } })).toEqual({});
  });
});

describe('orderOpMessage — approve refusals surface verbatim', () => {
  it('passes the typed refusal text through untouched', () => {
    expect(orderOpMessage({ data: { errno: 662, errmsg: 'order is not paid — nothing to approve' } })).toBe(
      'order is not paid — nothing to approve',
    );
    expect(orderOpMessage({ data: { errno: 0 } })).toBeNull();
  });

  it('reports transport errors with their status', () => {
    expect(orderOpMessage({ error: { status: 404 } })).toBe('Request failed (404)');
    expect(orderOpMessage({ error: { status: 502, data: { errmsg: 'order service unavailable' } } })).toBe('order service unavailable');
  });
});

describe('cjPlacementState', () => {
  it('only a paid (201) CJ order without stamp or cjOrderId awaits approval', () => {
    expect(cjPlacementState({ source: 'cj', orderStatus: 201 })).toBe('awaiting-approval');
  });

  it('never offers approval for non-CJ, unpaid, or refund-state orders', () => {
    expect(cjPlacementState(undefined)).toBe('not-applicable');
    expect(cjPlacementState({ source: 'local', orderStatus: 201 })).toBe('not-applicable');
    expect(cjPlacementState({ source: 'cj', orderStatus: 101 })).toBe('not-paid');
    // 202 refund-applied (e.g. prod order 9) must NOT be approvable.
    expect(cjPlacementState({ source: 'cj', orderStatus: 202 })).toBe('not-paid');
  });

  it('an approval stamp means approved-awaiting-placement; only cjOrderId means placed', () => {
    expect(cjPlacementState({ source: 'cj', orderStatus: 201, cjPlacementApprovedTime: '2026-08-08T10:00:00' })).toBe(
      'approved-awaiting-placement',
    );
    expect(cjPlacementState({ source: 'cj', orderStatus: 201, cjPlacementApprovedTime: [2026, 8, 8, 10, 0, 0] })).toBe(
      'approved-awaiting-placement',
    );
    expect(
      cjPlacementState({ source: 'cj', orderStatus: 301, cjPlacementApprovedTime: '2026-08-08T10:00:00', cjOrderId: 'CJ123' }),
    ).toBe('placed');
  });
});
