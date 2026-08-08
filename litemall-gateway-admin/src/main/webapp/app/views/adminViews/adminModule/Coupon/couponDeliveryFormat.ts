import { CouponSegment, ICouponDeliverResult, ICouponDelivery } from 'app/shared/reducers/private/services/adminPromotionApi';

// Wave 22: pure helpers for the "Deliver to segment" dialog + deliveries
// history (RFM-targeted coupon delivery). Kept view-free for unit tests.

const money = (v: number): string => `$${Number(v).toFixed(2)}`;

/** Raw dialog inputs (input[type=number] state — strings, blank = unset). */
export interface SegmentInputs {
  recencyDays: string;
  minFrequency: string;
  minMonetary: string;
}

const num = (s: string): number | undefined => (s.trim() === '' ? undefined : Number(s));

/** Inputs → the deliver command's segment; blanks are dropped entirely. */
export const segmentFromInputs = (i: SegmentInputs): CouponSegment => {
  const out: CouponSegment = {};
  const r = num(i.recencyDays);
  const f = num(i.minFrequency);
  const m = num(i.minMonetary);
  if (r != null) out.recencyDays = r;
  if (f != null) out.minFrequency = f;
  if (m != null) out.minMonetary = m;
  return out;
};

/** First blocking input error, or null. All criteria are optional; when set
 *  they must be positive (days/orders integral). */
export const segmentClientError = (i: SegmentInputs): string | null => {
  const r = num(i.recencyDays);
  if (r != null && (!Number.isInteger(r) || r <= 0)) return 'Recency must be a whole number of days (or left empty).';
  const f = num(i.minFrequency);
  if (f != null && (!Number.isInteger(f) || f <= 0)) return 'Frequency must be a whole number of orders (or left empty).';
  const m = num(i.minMonetary);
  if (m != null && (!Number.isFinite(m) || m <= 0)) return 'Spend must be a positive amount (or left empty).';
  return null;
};

/** Human label for a segment — honest about what each criterion means.
 *  Empty segment = every customer. */
export const segmentLabel = (segment?: CouponSegment | null): string => {
  if (!segment) return 'All customers';
  const parts: string[] = [];
  if (segment.recencyDays != null) parts.push(`bought within ${segment.recencyDays} day${segment.recencyDays === 1 ? '' : 's'}`);
  if (segment.minFrequency != null) parts.push(`at least ${segment.minFrequency} order${segment.minFrequency === 1 ? '' : 's'}`);
  if (segment.minMonetary != null) parts.push(`spent at least ${money(segment.minMonetary)}`);
  return parts.length > 0 ? parts.join(' · ') : 'All customers';
};

/** History rows store segment_json — a JSON string or already-parsed object;
 *  malformed JSON is shown raw rather than silently swallowed. */
export const deliverySegmentLabel = (d: ICouponDelivery): string => {
  const raw = d.segmentJson;
  if (raw == null || raw === '') return 'All customers';
  if (typeof raw === 'object') return segmentLabel(raw as CouponSegment);
  try {
    return segmentLabel(JSON.parse(raw) as CouponSegment);
  } catch {
    return String(raw);
  }
};

/** Result line: preview = count only (nothing was granted); real run =
 *  matched/granted/skipped verbatim counts. */
export const deliverResultText = (r: ICouponDeliverResult, preview: boolean): string => {
  const matched = r.matched ?? 0;
  if (preview) return `${matched} user${matched === 1 ? '' : 's'} match${matched === 1 ? 'es' : ''} this segment. Nothing has been granted yet.`;
  return `Matched ${matched} · granted ${r.granted ?? 0} · skipped ${r.skipped ?? 0}.`;
};
