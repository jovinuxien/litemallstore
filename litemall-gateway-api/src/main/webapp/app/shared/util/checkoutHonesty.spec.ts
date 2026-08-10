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

describe('courierDeltaLabel (renders options[].upgradeDelta verbatim)', () => {
  it('labels positive deltas in € and zero/negative as Included', () => {
    expect(courierDeltaLabel(3.2)).toBe('+€3.20');
    expect(courierDeltaLabel(0)).toBe('Included');
    expect(courierDeltaLabel(0.0)).toBe('Included');
    expect(courierDeltaLabel(-1)).toBe('Included'); // server never sends this, but never label a discount as an upcharge
  });

  it('returns null (no label) for null/absent/junk — unpriceable line or pre-24.1 order half', () => {
    expect(courierDeltaLabel(null)).toBeNull();
    expect(courierDeltaLabel(undefined)).toBeNull();
    expect(courierDeltaLabel(NaN)).toBeNull();
  });
});
