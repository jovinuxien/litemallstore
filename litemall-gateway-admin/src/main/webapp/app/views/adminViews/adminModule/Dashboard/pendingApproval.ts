import { IPendingCjPage } from 'app/shared/reducers/private/services/adminOrderCjApi';

// Dashboard "Pending CJ approval" surface (order's
// GET /srv/private/admin/order/cj-placement/pending). Paid CJ orders sit in the
// durable placement queue until an admin approves them, so this is the one
// number on the dashboard that represents money already taken and fulfilment
// not yet started — it belongs next to revenue, not buried in a tab.
//
// The state is derived here rather than inline so it can be tested without
// rendering, matching the pageFormat.ts pattern.

/** How many pending orders the dashboard card lists before deferring to the tab. */
export const DASHBOARD_PENDING_ROWS = 5;

export type PendingTileKind =
  /** the query is still in flight — show a spinner, never a number */
  | 'loading'
  /** the endpoint failed — show '—', never a fabricated 0 */
  | 'unavailable'
  /** the endpoint answered and nothing is waiting */
  | 'empty'
  /** orders are waiting for an admin decision */
  | 'pending';

export interface PendingTileState {
  kind: PendingTileKind;
  /** null whenever a number would be a guess (loading / endpoint down) */
  count: number | null;
  /** what to render in the tile body */
  display: string;
  /** muted line under the tile; empty when the count speaks for itself */
  note: string;
}

export interface PendingQueryView {
  isLoading?: boolean;
  isError?: boolean;
  data?: IPendingCjPage;
}

/**
 * Derive the tile from the query.
 *
 * <p>A failed fetch must NOT read as zero. OrderList coerces the error to 0 for
 * its tab badge, which is fine for a badge that merely decorates a visible tab —
 * but on the dashboard a bare "0" is a claim that no order is waiting, which is
 * exactly the wrong thing to tell an operator whose orders ARE waiting behind a
 * dead endpoint. An errno body (2xx + errno!==0) counts as unavailable for the
 * same reason.
 */
export const pendingTileState = (q: PendingQueryView): PendingTileState => {
  if (q.isLoading) {
    return { kind: 'loading', count: null, display: '…', note: '' };
  }
  if (q.isError || q.data?.errmsg || !q.data) {
    return {
      kind: 'unavailable',
      count: null,
      display: '—',
      note: q.data?.errmsg || 'Could not reach the order service',
    };
  }
  const count = q.data.total ?? 0;
  return count === 0
    ? { kind: 'empty', count: 0, display: '0', note: 'Nothing waiting' }
    : { kind: 'pending', count, display: String(count), note: count === 1 ? '1 order needs approval' : `${count} orders need approval` };
};

/** Rows to list on the dashboard card — capped; the tab owns the full page. */
export const dashboardPendingRows = (page: IPendingCjPage | undefined, max = DASHBOARD_PENDING_ROWS) =>
  page?.errmsg ? [] : (page?.list ?? []).slice(0, max);

/** How many pending orders the card is NOT showing (drives "View all"). */
export const pendingOverflow = (state: PendingTileState, shown: number): number =>
  state.count == null ? 0 : Math.max(0, state.count - shown);
