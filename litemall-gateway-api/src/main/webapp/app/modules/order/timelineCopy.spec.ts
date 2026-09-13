import { describeTimelineEntry, isKnownChangeType, toneOf } from './timelineCopy';

/**
 * Order lifecycle contract §4: every change type the order side writes has a headline;
 * the server's message is the detail, verbatim; unknown types still render honestly.
 */
describe('describeTimelineEntry', () => {
  it('knows every change type the aggregate and the lifecycle packages write', () => {
    const types = [
      'create', 'pay', 'cancel', 'system_cancel', 'ship', 'receive', 'auto_receive', 'writeoff',
      'refund_request', 'refund_withdrawn', 'refund', 'cj_placement', 'cj_placement_failed', 'cj_stall',
      'cj_sync', 'payment_reconciled', 'payment_refunded_stray', 'payment_stray_unrefunded',
    ];
    for (const type of types) {
      expect(isKnownChangeType(type)).toBe(true);
      const line = describeTimelineEntry({ changeType: type });
      expect(line.headline).not.toMatch(/timeline\./); // a real sentence, not a raw key
      expect(line.headline.length).toBeGreaterThan(3);
    }
  });

  it('the server message is the detail, verbatim, unless it just repeats the headline', () => {
    const cj = describeTimelineEntry(
      { changeType: 'cj_placement_failed', changeMessage: 'CJ rejected the order: Please enter a IOSS number (7001)', operator: 'system' }
    );
    expect(cj.headline).toBe('Fulfilment delayed — our team is on it');
    expect(cj.detail).toBe('CJ rejected the order: Please enter a IOSS number (7001)');
    expect(cj.who).toBe('Automatic');
    expect(cj.tone).toBe('warn');

    const pay = describeTimelineEntry({ changeType: 'pay', changeMessage: 'Payment received', operator: 'user' });
    expect(pay.headline).toBe('Payment received');
    expect(pay.detail).toBeUndefined();
    expect(pay.who).toBe('You');
    expect(pay.tone).toBe('good');
  });

  it('operators map to plain words; admin ids are never shown', () => {
    expect(describeTimelineEntry({ changeType: 'refund', operator: 'admin:3' }).who).toBe('Our team');
    expect(describeTimelineEntry({ changeType: 'refund', operator: 'admin' }).who).toBe('Our team');
    expect(describeTimelineEntry({ changeType: 'refund', operator: null }).who).toBeUndefined();
  });

  it('an unknown type renders the server status label, then the message, never a raw key', () => {
    expect(describeTimelineEntry({ changeType: 'brand_new_hop', toStatusText: 'PAID', changeMessage: 'something' }).headline).toBe('PAID');
    expect(describeTimelineEntry({ changeType: 'brand_new_hop', changeMessage: 'only a message' }).headline).toBe('only a message');
    expect(describeTimelineEntry({ changeType: 'brand_new_hop' }).headline).toBe('Status updated');
    expect(toneOf('brand_new_hop')).toBe('normal');
  });
});
