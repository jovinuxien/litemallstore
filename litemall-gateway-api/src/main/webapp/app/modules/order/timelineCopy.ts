import { t } from 'app/i18n';
import { IOrderTimelineEntry } from 'app/shared/model/order/order.model';

/**
 * Plain-sentence rendering of `GET /srv/order/{id}/timeline` entries (order lifecycle
 * contract §4). The change TYPE is the stable vocabulary (aggregate transitions plus the
 * package-B/C types: cj_placement, cj_placement_failed, cj_stall, cj_sync,
 * payment_reconciled, payment_refunded_stray, payment_stray_unrefunded, refund_withdrawn);
 * it picks a localised headline. The change MESSAGE is the server's own account of that
 * step — CJ's reason, the intent id, the carrier — and is shown verbatim as the detail
 * line, never mapped. An unknown type falls back to the server's status label, so a new
 * hop type renders honestly before this file learns about it.
 */
const KNOWN_TYPES = [
  'create',
  'pay',
  'cancel',
  'system_cancel',
  'ship',
  'receive',
  'auto_receive',
  'writeoff',
  'refund_request',
  'refund_withdrawn',
  'refund',
  'cj_placement',
  'cj_placement_failed',
  'cj_stall',
  'cj_sync',
  'payment_reconciled',
  'payment_refunded_stray',
  'payment_stray_unrefunded',
] as const;

export type KnownChangeType = (typeof KNOWN_TYPES)[number];

export const isKnownChangeType = (type: string | null | undefined): type is KnownChangeType =>
  (KNOWN_TYPES as readonly string[]).includes(type ?? '');

/** Attention level for the marker: something the customer may need to act on or know. */
export type TimelineTone = 'normal' | 'warn' | 'good';

export const toneOf = (type: string | null | undefined): TimelineTone => {
  switch (type) {
    case 'cj_placement_failed':
    case 'cj_stall':
    case 'payment_stray_unrefunded':
    case 'cancel':
    case 'system_cancel':
      return 'warn';
    case 'pay':
    case 'payment_reconciled':
    case 'payment_refunded_stray':
    case 'receive':
    case 'auto_receive':
    case 'writeoff':
    case 'refund':
      return 'good';
    default:
      return 'normal';
  }
};

export interface TimelineLine {
  headline: string;
  /** The server's message, when it adds something the headline does not already say. */
  detail?: string;
  /** Who did it, localised ("You" / "Automatic" / "Our team"), or undefined when unknown. */
  who?: string;
  tone: TimelineTone;
}

/**
 * Uses the module-level `t` (explicit `order:` namespace) so the key parser can see every
 * key; callers re-render on a language switch through their own useTranslation.
 */
export const describeTimelineEntry = (entry: IOrderTimelineEntry): TimelineLine => {
  const type = entry.changeType ?? '';
  const message = (entry.changeMessage ?? '').trim();
  const headline = isKnownChangeType(type)
    ? t(`order:timeline.types.${type}`)
    : (entry.toStatusText ?? '').trim() || message || t('order:timeline.types.unknown');
  // Don't print the same sentence twice: the aggregate's own messages ("Payment
  // received") often equal the headline.
  const detail = message && message.toLowerCase() !== headline.toLowerCase() ? message : undefined;
  const op = (entry.operator ?? '').trim().toLowerCase();
  let who: string | undefined;
  if (op === 'user') who = t('order:timeline.who.you');
  else if (op === 'system') who = t('order:timeline.who.system');
  else if (op.startsWith('admin')) who = t('order:timeline.who.team');
  return { headline, detail, who, tone: toneOf(type) };
};
