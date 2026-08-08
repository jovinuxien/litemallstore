import { describe, expect, it } from '@jest/globals';

import { deliverResultText, deliverySegmentLabel, segmentClientError, segmentFromInputs, segmentLabel } from './couponDeliveryFormat';

// Wave 22: segment inputs → deliver command, honest segment labels, and the
// preview/deliver result lines.

const inputs = (recencyDays = '', minFrequency = '', minMonetary = '') => ({ recencyDays, minFrequency, minMonetary });

describe('segmentFromInputs', () => {
  it('drops blank criteria entirely (all optional)', () => {
    expect(segmentFromInputs(inputs())).toEqual({});
    expect(segmentFromInputs(inputs('30', '', ''))).toEqual({ recencyDays: 30 });
  });

  it('converts every set criterion to a number', () => {
    expect(segmentFromInputs(inputs('30', '2', '50.5'))).toEqual({ recencyDays: 30, minFrequency: 2, minMonetary: 50.5 });
  });
});

describe('segmentClientError', () => {
  it('accepts an empty segment (every customer) and valid values', () => {
    expect(segmentClientError(inputs())).toBeNull();
    expect(segmentClientError(inputs('30', '2', '50'))).toBeNull();
  });

  it('rejects non-positive or fractional days/orders', () => {
    expect(segmentClientError(inputs('0'))).toMatch(/whole number of days/);
    expect(segmentClientError(inputs('1.5'))).toMatch(/whole number of days/);
    expect(segmentClientError(inputs('', '-1'))).toMatch(/whole number of orders/);
    expect(segmentClientError(inputs('', '', '0'))).toMatch(/positive amount/);
    expect(segmentClientError(inputs('', '', 'abc'))).toMatch(/positive amount/);
  });
});

describe('segmentLabel', () => {
  it('labels each criterion honestly and joins them', () => {
    expect(segmentLabel({ recencyDays: 30, minFrequency: 2, minMonetary: 50 })).toBe(
      'bought within 30 days · at least 2 orders · spent at least $50.00'
    );
  });

  it('uses singular forms', () => {
    expect(segmentLabel({ recencyDays: 1, minFrequency: 1 })).toBe('bought within 1 day · at least 1 order');
  });

  it('is "All customers" for an empty segment', () => {
    expect(segmentLabel({})).toBe('All customers');
    expect(segmentLabel(undefined)).toBe('All customers');
  });
});

describe('deliverySegmentLabel', () => {
  it('parses a stored JSON string', () => {
    expect(deliverySegmentLabel({ segmentJson: '{"recencyDays":7,"minMonetary":25}' })).toBe('bought within 7 days · spent at least $25.00');
  });

  it('accepts an already-parsed object and empty storage', () => {
    expect(deliverySegmentLabel({ segmentJson: { minFrequency: 3 } })).toBe('at least 3 orders');
    expect(deliverySegmentLabel({ segmentJson: '{}' })).toBe('All customers');
    expect(deliverySegmentLabel({})).toBe('All customers');
  });

  it('shows malformed JSON raw instead of swallowing it', () => {
    expect(deliverySegmentLabel({ segmentJson: 'not-json' })).toBe('not-json');
  });
});

describe('deliverResultText', () => {
  it('preview reports the match count and that nothing was granted', () => {
    expect(deliverResultText({ matched: 132 }, true)).toBe('132 users match this segment. Nothing has been granted yet.');
    expect(deliverResultText({ matched: 1 }, true)).toBe('1 user matches this segment. Nothing has been granted yet.');
  });

  it('real run reports matched/granted/skipped verbatim', () => {
    expect(deliverResultText({ matched: 132, granted: 120, skipped: 12 }, false)).toBe('Matched 132 · granted 120 · skipped 12.');
    expect(deliverResultText({}, false)).toBe('Matched 0 · granted 0 · skipped 0.');
  });
});
