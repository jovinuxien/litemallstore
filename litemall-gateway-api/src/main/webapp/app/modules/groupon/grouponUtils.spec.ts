import { ICombinationPink } from 'app/shared/api';

import {
  activeSlotFor,
  canInvite,
  fmtTimeLeft,
  inviteLeaderId,
  inviteLink,
  serverTimeToEpoch,
  slotCta,
  toDisplayTime,
} from './grouponUtils';

/**
 * Wave-21 pure helpers: server-time parsing (ISO string vs Jackson number[]
 * tuple), the slot-state → CTA mapping the landing/PDP/checkout surfaces key
 * on, invite eligibility and the shareable join link.
 */

const NOW = new Date(2026, 7, 8, 12, 0, 0).getTime(); // 2026-08-08 12:00 local

const slot = (over: Partial<ICombinationPink> = {}): ICombinationPink => ({
  pinkId: 501,
  combinationId: 7,
  headId: 501,
  userId: 42,
  orderId: null,
  requiredMembers: 3,
  memberCount: 1,
  expireTime: [2026, 8, 8, 13, 0, 0], // one hour after NOW
  status: 'Pending',
  ...over,
});

describe('serverTimeToEpoch', () => {
  it('parses a Jackson number[] tuple as local time', () => {
    expect(serverTimeToEpoch([2026, 8, 8, 13, 0, 0])).toBe(new Date(2026, 7, 8, 13, 0, 0).getTime());
  });

  it('parses an ISO string', () => {
    expect(serverTimeToEpoch('2026-08-08T13:00:00')).toBe(new Date(2026, 7, 8, 13, 0, 0).getTime());
  });

  it('returns null for missing or junk input', () => {
    expect(serverTimeToEpoch(undefined)).toBeNull();
    expect(serverTimeToEpoch(null)).toBeNull();
    expect(serverTimeToEpoch('not-a-date')).toBeNull();
  });
});

describe('toDisplayTime', () => {
  it('renders a tuple as "YYYY-MM-DD HH:mm"', () => {
    expect(toDisplayTime([2026, 8, 8, 9, 5])).toBe('2026-08-08 09:05');
  });

  it('renders an ISO string without the T', () => {
    expect(toDisplayTime('2026-08-08T09:05:00')).toBe('2026-08-08 09:05');
  });

  it('is empty for missing input', () => {
    expect(toDisplayTime(undefined)).toBe('');
  });
});

describe('fmtTimeLeft', () => {
  it('formats days, hours and minutes', () => {
    expect(fmtTimeLeft(NOW + 2 * 86400000 + 4 * 3600000, NOW)).toBe('2d 4h');
    expect(fmtTimeLeft(NOW + 3 * 3600000 + 12 * 60000, NOW)).toBe('3h 12m');
    expect(fmtTimeLeft(NOW + 8 * 60000, NOW)).toBe('8m');
  });

  it('is null once past or unknown', () => {
    expect(fmtTimeLeft(NOW - 1, NOW)).toBeNull();
    expect(fmtTimeLeft(null, NOW)).toBeNull();
  });
});

describe('slotCta', () => {
  it('maps a live Pending slot to checkout (pay-at-submit is allowed while Pending)', () => {
    expect(slotCta(slot(), NOW)).toBe('checkout');
  });

  it('maps a Success slot without an order to checkout', () => {
    expect(slotCta(slot({ status: 'Success' }), NOW)).toBe('checkout');
  });

  it('maps a slot with an attached order to ordered (one order per slot)', () => {
    expect(slotCta(slot({ orderId: 9001 }), NOW)).toBe('ordered');
    expect(slotCta(slot({ status: 'Success', orderId: 9001 }), NOW)).toBe('ordered');
  });

  it('maps a Failed slot to expired', () => {
    expect(slotCta(slot({ status: 'Failed' }), NOW)).toBe('expired');
  });

  it('maps a Pending slot past its expireTime to expired', () => {
    expect(slotCta(slot({ expireTime: [2026, 8, 8, 11, 59, 0] }), NOW)).toBe('expired');
  });

  it('treats a missing status as Pending', () => {
    expect(slotCta(slot({ status: undefined }), NOW)).toBe('checkout');
  });

  it('is none without a slot', () => {
    expect(slotCta(null, NOW)).toBe('none');
    expect(slotCta({}, NOW)).toBe('none');
  });
});

describe('canInvite', () => {
  it('allows inviting on a live, not-yet-full Pending slot', () => {
    expect(canInvite(slot(), NOW)).toBe(true);
  });

  it('refuses once the group is full, expired, ordered or not Pending', () => {
    expect(canInvite(slot({ memberCount: 3 }), NOW)).toBe(false);
    expect(canInvite(slot({ expireTime: [2026, 8, 8, 11, 0, 0] }), NOW)).toBe(false);
    expect(canInvite(slot({ orderId: 9001 }), NOW)).toBe(false);
    expect(canInvite(slot({ status: 'Success' }), NOW)).toBe(false);
    expect(canInvite(null, NOW)).toBe(false);
  });
});

describe('invite link', () => {
  it('uses the group leader pinkId (headId) as the join id', () => {
    expect(inviteLeaderId(slot({ pinkId: 502, headId: 501 }))).toBe(501);
    expect(inviteLeaderId(slot({ pinkId: 501, headId: undefined }))).toBe(501);
  });

  it('builds the SPA-relative /groupon/:id?join= link', () => {
    expect(inviteLink(7, 501)).toBe('/groupon/7?join=501');
  });
});

describe('activeSlotFor', () => {
  it('prefers an actionable (checkout) slot over ordered and expired ones', () => {
    const expired = slot({ pinkId: 1, status: 'Failed' });
    const ordered = slot({ pinkId: 2, orderId: 9001 });
    const live = slot({ pinkId: 3 });
    expect(activeSlotFor([expired, ordered, live], 7, NOW)?.pinkId).toBe(3);
    expect(activeSlotFor([expired, ordered], 7, NOW)?.pinkId).toBe(2);
    expect(activeSlotFor([expired], 7, NOW)?.pinkId).toBe(1);
  });

  it('only considers slots of the requested campaign', () => {
    const other = slot({ pinkId: 9, combinationId: 99 });
    expect(activeSlotFor([other], 7, NOW)).toBeNull();
  });

  it('is null without slots or a campaign id', () => {
    expect(activeSlotFor([], 7, NOW)).toBeNull();
    expect(activeSlotFor([slot()], undefined, NOW)).toBeNull();
    expect(activeSlotFor(null, 7, NOW)).toBeNull();
  });
});
