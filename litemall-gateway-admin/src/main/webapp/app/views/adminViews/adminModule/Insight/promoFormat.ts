import { ICombination, ICoupon } from 'app/shared/model/admin/promotion-system.model';
import { IPromoCandidate, PromoKind } from 'app/shared/reducers/private/services/insightApi';
import { DISCOUNT_FLAT, SCOPE_CATEGORY, SCOPE_GOODS } from 'app/views/adminViews/adminModule/Coupon/couponFormat';
import { money } from 'app/shared/util/money';

// Wave 19: pure render/prefill helpers for the promo-suggestions panel
// (/srv/private/admin/insight/promo-candidates). Kept view-free so they are
// unit-testable. The nightly scorer's `suggestion` is PRE-VALIDATED against
// the Wave-18 margin guard server-side; these helpers only render it and map
// it onto the EXISTING CouponForm / GrouponRuleForm via router state.

const rate = (v: number): string => (Number.isInteger(v) ? String(v) : Number(v).toFixed(1));

// ---- human suggestion sentence ---------------------------------------------

// "10% off Women's Clothing, cap €5.00, min spend €30.00" |
// "€4.00 off this product" | "group price €12.99, 3 members, 7-day window".
export const fmtPromoSuggestion = (c: IPromoCandidate, categoryName?: string): string => {
  const s = c.suggestion;
  if (!s) return '—';
  if (c.kind === 'groupon') {
    const parts: string[] = [];
    if (s.combinationPrice != null) parts.push(`group price ${money(s.combinationPrice)}`);
    if (s.requiredMembers != null) parts.push(`${s.requiredMembers} members`);
    if (s.windowDays != null) parts.push(`${s.windowDays}-day window`);
    if (s.limitPerUser != null) parts.push(`limit ${s.limitPerUser}/user`);
    return parts.length > 0 ? parts.join(', ') : '—';
  }
  if (s.discount == null) return '—';
  const isPercent = (s.discountType ?? DISCOUNT_FLAT) === 1;
  const scope =
    s.scopeType === 'category'
      ? categoryName || (s.categoryId != null ? `category #${s.categoryId}` : 'its category')
      : (s.goodsIds?.length ?? 1) > 1
        ? `${s.goodsIds?.length} products`
        : 'this product';
  const parts = [`${isPercent ? `${rate(s.discount)}% off` : `${money(s.discount)} off`} ${scope}`];
  if (isPercent && s.discountCap != null) parts.push(`cap ${money(s.discountCap)}`);
  if (s.minAmount != null && s.minAmount > 0) parts.push(`min spend ${money(s.minAmount)}`);
  return parts.join(', ');
};

// ---- router-state prefill contract -----------------------------------------
// The panel navigates to the EXISTING forms with `{ promoPrefill }` in router
// state; the forms initialise from it (no behavior change when absent) and
// fire `consume` fail-soft after a successful create.

export interface PromoConsumeRef {
  kind: PromoKind;
  goodsId: number;
  day?: string;
}

export interface CouponPromoPrefill {
  coupon: Partial<ICoupon>;
  consume: PromoConsumeRef;
}

export interface GrouponPromoPrefill {
  combination: Partial<ICombination>;
  consume: PromoConsumeRef;
}

export const couponPrefill = (c: IPromoCandidate, categoryName?: string): CouponPromoPrefill => {
  const s = c.suggestion ?? {};
  const goodsScoped = s.scopeType === 'goods';
  return {
    coupon: {
      name: fmtPromoSuggestion(c, categoryName),
      goodsType: goodsScoped ? SCOPE_GOODS : SCOPE_CATEGORY,
      goodsValue: goodsScoped ? (s.goodsIds?.length ? s.goodsIds : [c.goodsId]) : s.categoryId != null ? [s.categoryId] : [],
      discountType: s.discountType ?? DISCOUNT_FLAT,
      discount: s.discount ?? 0,
      discountCap: s.discountCap ?? undefined,
      min: s.minAmount ?? 0,
    },
    consume: { kind: 'coupon', goodsId: c.goodsId, day: c.day },
  };
};

// Local 'YYYY-MM-DDTHH:mm' — what the forms' datetime-local inputs hold.
const dtLocal = (d: Date): string => {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
};

// New campaigns start in DRAFT (activation stays a separate list-view action —
// the Phase-3 gating decision); the window runs now → now + windowDays.
export const grouponPrefill = (c: IPromoCandidate, now: Date = new Date()): GrouponPromoPrefill => {
  const s = c.suggestion ?? {};
  const windowDays = s.windowDays ?? 7;
  return {
    combination: {
      goodsId: c.goodsId,
      title: c.name ? `Group buy: ${c.name}` : `Group buy: goods #${c.goodsId}`,
      picUrl: c.picUrl ?? '',
      combinationPrice: s.combinationPrice ?? 0,
      originalPrice: s.originalPrice ?? c.retailPrice ?? 0,
      requiredMembers: s.requiredMembers ?? 2,
      limitPerUser: s.limitPerUser ?? 1,
      startTime: dtLocal(now),
      endTime: dtLocal(new Date(now.getTime() + windowDays * 24 * 60 * 60 * 1000)),
    },
    consume: { kind: 'groupon', goodsId: c.goodsId, day: c.day },
  };
};

// The created coupon/combination id out of a promotion mutation result
// ({success, …, data:{couponId|combinationId}}); undefined when absent so a
// consume without refId still records the decision.
export const createdRefId = (res: unknown, key: 'couponId' | 'combinationId'): number | undefined => {
  const data = (res as { data?: { data?: Record<string, unknown> } })?.data?.data;
  const n = Number(data?.[key]);
  return Number.isFinite(n) && n > 0 ? n : undefined;
};
