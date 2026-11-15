import { __resetLocale, applyEnabledLanguages, setLocale } from 'app/i18n/locale';

import { couponConditionLabel, couponDiscountLabel, couponPickerLabel, couponScopeLink, couponValueShort } from './couponFormat';

jest.mock('app/shared/config/siteConfig', () => ({ loadSiteConfig: jest.fn().mockResolvedValue({ i18nLanguages: ['en'] }) }));

/**
 * i18n batch 3 — the coupon wording has ONE home (couponFormat.ts) shared by the PDP
 * strip, the coupon center, My coupons and the checkout picker. The Wave-18 contract
 * facts are unchanged: flat vs percent, the selectlist exception, pre-Wave-18 payloads.
 */
const flat = { id: 1, discount: 5, min: 50 } as any;
const pct = { id: 2, discountType: 1, discount: 15, discountCap: 20, min: 0 } as any;

beforeEach(async () => {
  await __resetLocale();
  await applyEnabledLanguages(['sv', 'da']);
});
afterAll(async () => {
  await __resetLocale();
});

it('renders the English contract unchanged', () => {
  expect(couponValueShort(flat)).toBe('€5');
  expect(couponValueShort(pct)).toBe('15%');
  expect(couponDiscountLabel(flat)).toBe('€5 off');
  expect(couponDiscountLabel(pct)).toBe('15% off (up to €20)');
  expect(couponConditionLabel(flat)).toBe('Spend €50');
  expect(couponConditionLabel(pct)).toBe('No minimum · up to €20');
  expect(couponPickerLabel({ ...pct, name: 'Autumn', discount: 7.5, min: 30 })).toBe('Autumn — −€7.5 (over €30) · percent coupon, up to €20');
  expect(couponScopeLink({ goodsType: 1, goodsValue: [1036143] } as any)).toEqual({ to: '/category/1036143', label: 'Shop eligible items' });
});

it('follows the locale, amounts untouched', async () => {
  await setLocale('sv');
  expect(couponDiscountLabel(pct)).toBe('15 % rabatt (upp till €20)');
  expect(couponConditionLabel(flat)).toBe('Handla för €50');
  expect(couponScopeLink({} as any).label).toBe('Visa alla produkter');
  await setLocale('da');
  expect(couponDiscountLabel(flat)).toBe('€5 rabat');
  expect(couponConditionLabel(pct)).toBe('Intet minimumsbeløb · op til €20');
});
