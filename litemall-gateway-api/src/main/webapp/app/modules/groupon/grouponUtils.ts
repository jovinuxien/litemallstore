import { ICombinationPink } from 'app/shared/api';

/**
 * Wave-21 group-buy helpers — pure functions shared by the /groupon/:id
 * landing, the PDP group-buy strip and checkout's group banner. No price math
 * beyond displaying server/campaign figures; slot state drives the CTA only —
 * the order service is the authority at submit (a stale slot is rejected
 * there with a typed message, never silently re-priced).
 */

/** LocalDateTime arrives as an ISO string or a Jackson number[] tuple → epoch ms (null when unparseable). */
export const serverTimeToEpoch = (t?: string | number[] | null): number | null => {
  if (t == null) return null;
  if (Array.isArray(t)) {
    const [y, mo, d, h = 0, mi = 0, s = 0] = t;
    if (!Number.isFinite(y) || !Number.isFinite(mo) || !Number.isFinite(d)) return null;
    return new Date(y, (mo as number) - 1, d, h as number, mi as number, s as number).getTime();
  }
  const ms = Date.parse(String(t));
  return Number.isFinite(ms) ? ms : null;
};

/** "2026-08-08 12:30" display form of a server LocalDateTime. */
export const toDisplayTime = (t?: string | number[] | null): string => {
  if (Array.isArray(t)) {
    const [y, mo, d, h = 0, mi = 0] = t;
    return `${y}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}`;
  }
  return t ? String(t).replace('T', ' ').slice(0, 16) : '';
};

/** "2d 4h" / "3h 12m" / "8m" remaining; null once past (or unknown). */
export const fmtTimeLeft = (endEpoch: number | null, now: number): string | null => {
  if (endEpoch == null) return null;
  const ms = endEpoch - now;
  if (ms <= 0) return null;
  const m = Math.max(1, Math.floor(ms / 60000));
  if (m >= 2880) return `${Math.floor(m / 1440)}d ${Math.floor((m % 1440) / 60)}h`;
  if (m >= 60) return `${Math.floor(m / 60)}h ${m % 60}m`;
  return `${m}m`;
};

/**
 * What the visitor can do with a slot they hold:
 * - 'checkout': slot is live (Pending not yet expired, or Success) and has no
 *   order — buying at the group price is possible (pay-at-submit).
 * - 'ordered': an order is already attached (one order per slot).
 * - 'expired': the group failed or ran past its expire time — the typed
 *   "start a new one or buy at regular price" path.
 */
export type SlotCta = 'checkout' | 'ordered' | 'expired' | 'none';

export const slotCta = (pink: ICombinationPink | null | undefined, now: number): SlotCta => {
  if (pink?.pinkId == null) return 'none';
  if (pink.orderId != null) return 'ordered';
  const status = pink.status ?? 'Pending';
  if (status === 'Success') return 'checkout';
  if (status !== 'Pending') return 'expired'; // Failed / anything terminal
  const expire = serverTimeToEpoch(pink.expireTime);
  return expire != null && expire <= now ? 'expired' : 'checkout';
};

/** A slot can still recruit members: Pending, not expired, not yet full. */
export const canInvite = (pink: ICombinationPink | null | undefined, now: number): boolean => {
  if (pink?.pinkId == null || pink.orderId != null) return false;
  if ((pink.status ?? 'Pending') !== 'Pending') return false;
  const expire = serverTimeToEpoch(pink.expireTime);
  if (expire != null && expire <= now) return false;
  const required = pink.requiredMembers ?? Infinity;
  return (pink.memberCount ?? 1) < required;
};

/** The invite id friends join with — the group leader's pinkId. */
export const inviteLeaderId = (pink: ICombinationPink): number | undefined => pink.headId ?? pink.pinkId;

/** SPA-relative shareable join link for a slot's group. */
export const inviteLink = (combinationId: number, leaderPinkId: number): string =>
  `/groupon/${combinationId}?join=${leaderPinkId}`;

const CTA_PREFERENCE: SlotCta[] = ['checkout', 'ordered', 'expired'];

/**
 * The visitor's most relevant slot for a campaign: an actionable (checkout)
 * slot wins, then an ordered one, then an expired one; ties keep the list
 * order (`/combination/my` is newest-first).
 */
export const activeSlotFor = (
  pinks: ICombinationPink[] | null | undefined,
  combinationId: number | undefined,
  now: number
): ICombinationPink | null => {
  if (!pinks?.length || combinationId == null) return null;
  const mine = pinks.filter(p => p.combinationId === combinationId);
  for (const wanted of CTA_PREFERENCE) {
    const hit = mine.find(p => slotCta(p, now) === wanted);
    if (hit) return hit;
  }
  return null;
};
