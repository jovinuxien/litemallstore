import { courierDeltaLabel, parseSelectlist, unusableReasonLabel } from './checkoutHonesty';

describe('parseSelectlist (verbose + legacy tolerance)', () => {
  it('passes the legacy bare array through as usable-only', () => {
    const legacy = [{ id: 1 }, { id: 2 }] as any;
    expect(parseSelectlist(legacy)).toEqual({ usable: legacy, unusable: [] });
  });

  it('splits the verbose envelope', () => {
    const res = { usable: [{ id: 1 }], unusable: [{ id: 2, reason: 'threshold', minGap: 12.5 }] } as any;
    expect(parseSelectlist(res).usable).toHaveLength(1);
    expect(parseSelectlist(res).unusable[0].reason).toBe('threshold');
  });

  it('never throws on junk', () => {
    expect(parseSelectlist(null)).toEqual({ usable: [], unusable: [] });
    expect(parseSelectlist('x')).toEqual({ usable: [], unusable: [] });
    expect(parseSelectlist({ usable: 'nope' })).toEqual({ usable: [], unusable: [] });
  });
});

describe('unusableReasonLabel', () => {
  it('renders the threshold gap in €', () => {
    expect(unusableReasonLabel('threshold', 12.5)).toBe('Spend €12.50 more to use this coupon');
    expect(unusableReasonLabel('threshold')).toBe('Order total is below this coupon’s minimum');
  });

  it('maps the other typed reasons and defaults honestly', () => {
    expect(unusableReasonLabel('scope')).toBe('Not valid for these items');
    expect(unusableReasonLabel('expired')).toBe('Expired');
    expect(unusableReasonLabel('exhausted')).toBe('Fully claimed');
    expect(unusableReasonLabel('mystery')).toBe('Not usable for this order');
    expect(unusableReasonLabel(undefined)).toBe('Not usable for this order');
  });
});

describe('courierDeltaLabel', () => {
  it('labels upgrades with the € delta and the default as Included', () => {
    expect(courierDeltaLabel(9.13, 5.93)).toBe('+€3.20');
    expect(courierDeltaLabel(5.93, 5.93)).toBe('Included');
    expect(courierDeltaLabel(4.0, 5.93)).toBe('Included'); // cheaper than default never charges extra
  });

  it('returns null (no label) while option prices are absent — pre-24.1 order half', () => {
    expect(courierDeltaLabel(undefined, 5.93)).toBeNull();
    expect(courierDeltaLabel(9.13, undefined)).toBeNull();
    expect(courierDeltaLabel(NaN, 5)).toBeNull();
  });
});
