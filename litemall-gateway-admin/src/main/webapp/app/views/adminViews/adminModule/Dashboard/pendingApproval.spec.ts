import { describe, expect, it } from '@jest/globals';

import { IPendingCjPage } from 'app/shared/reducers/private/services/adminOrderCjApi';

import { DASHBOARD_PENDING_ROWS, dashboardPendingRows, pendingOverflow, pendingTileState } from './pendingApproval';

// Dashboard pending-CJ-approval tile + card. The load-bearing rule is that a
// failed fetch must never render as "0 pending" — that would tell an operator
// nothing is waiting while paid orders sit unfulfilled behind a dead endpoint.

const page = (over: Partial<IPendingCjPage> = {}): IPendingCjPage => ({
  list: [],
  total: 0,
  pages: 0,
  ...over,
});

const row = (orderId: number) => ({ orderId, orderSn: `sn-${orderId}`, items: [] });

describe('pendingTileState', () => {
  it('shows a count when orders are waiting', () => {
    const state = pendingTileState({ data: page({ total: 3, list: [row(1)] }) });
    expect(state.kind).toBe('pending');
    expect(state.count).toBe(3);
    expect(state.display).toBe('3');
    expect(state.note).toBe('3 orders need approval');
  });

  it('says it in the singular for exactly one', () => {
    expect(pendingTileState({ data: page({ total: 1 }) }).note).toBe('1 order needs approval');
  });

  it('reports a real zero as empty', () => {
    const state = pendingTileState({ data: page({ total: 0 }) });
    expect(state.kind).toBe('empty');
    expect(state.count).toBe(0);
    expect(state.display).toBe('0');
  });

  it('withholds a number while loading', () => {
    const state = pendingTileState({ isLoading: true });
    expect(state.kind).toBe('loading');
    expect(state.count).toBeNull();
  });

  it('NEVER renders a transport failure as zero pending', () => {
    const state = pendingTileState({ isError: true });
    expect(state.kind).toBe('unavailable');
    expect(state.count).toBeNull();
    expect(state.display).toBe('—');
    expect(state.display).not.toBe('0');
  });

  it('NEVER renders an errno body as zero pending, and surfaces its message', () => {
    const state = pendingTileState({ data: page({ errmsg: 'order service unavailable' }) });
    expect(state.kind).toBe('unavailable');
    expect(state.count).toBeNull();
    expect(state.note).toBe('order service unavailable');
  });

  it('treats a missing payload as unavailable rather than empty', () => {
    expect(pendingTileState({}).kind).toBe('unavailable');
  });
});

describe('dashboardPendingRows', () => {
  it('caps the card at the dashboard row limit', () => {
    const list = Array.from({ length: DASHBOARD_PENDING_ROWS + 4 }, (_, i) => row(i + 1));
    expect(dashboardPendingRows(page({ list, total: list.length }))).toHaveLength(DASHBOARD_PENDING_ROWS);
  });

  it('lists nothing for an errno body (the alert speaks instead)', () => {
    expect(dashboardPendingRows(page({ list: [row(1)], errmsg: 'boom' }))).toEqual([]);
  });

  it('tolerates an absent page', () => {
    expect(dashboardPendingRows(undefined)).toEqual([]);
  });
});

describe('pendingOverflow', () => {
  it('counts what the card is not showing', () => {
    expect(pendingOverflow(pendingTileState({ data: page({ total: 12 }) }), 5)).toBe(7);
  });

  it('is zero when the card shows everything', () => {
    expect(pendingOverflow(pendingTileState({ data: page({ total: 3 }) }), 3)).toBe(0);
  });

  it('never goes negative, and claims no overflow when the count is unknown', () => {
    expect(pendingOverflow(pendingTileState({ data: page({ total: 2 }) }), 5)).toBe(0);
    expect(pendingOverflow(pendingTileState({ isError: true }), 0)).toBe(0);
  });
});
