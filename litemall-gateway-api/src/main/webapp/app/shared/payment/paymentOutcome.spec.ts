import { classifyIntentRefusal, redirectOutcome, settlementOf } from './paymentOutcome';

describe('classifyIntentRefusal', () => {
  it('reads the two non-failure 402s by their contract wording', () => {
    expect(classifyIntentRefusal('Order 12 has already been paid — refresh the page to see it.')).toBe('already-paid');
    expect(
      classifyIntentRefusal('A payment for order 12 is still being processed by your bank. Please wait for the confirmation email before paying again.')
    ).toBe('processing');
  });
  it('treats anything else — including blank — as a real refusal', () => {
    expect(classifyIntentRefusal('Card payments are not enabled')).toBe('other');
    expect(classifyIntentRefusal('')).toBe('other');
    expect(classifyIntentRefusal(undefined)).toBe('other');
  });
});

describe('redirectOutcome', () => {
  const p = (q: string) => redirectOutcome(new URLSearchParams(q));
  it('only a failed redirect or our own fail marker is terminal', () => {
    expect(p('redirect_status=failed')).toBe('failed');
    expect(p('status=fail')).toBe('failed');
    expect(p('result=cancel')).toBe('failed');
  });
  it('processing is carried from Stripe or from the checkout hand-off', () => {
    expect(p('redirect_status=processing')).toBe('processing');
    expect(p('status=processing')).toBe('processing');
  });
  it('a succeeded redirect is NOT trusted — it still polls', () => {
    expect(p('redirect_status=succeeded&payment_intent=pi_1')).toBe('pending');
    expect(p('status=success')).toBe('pending');
    expect(p('')).toBe('pending');
  });
});

describe('settlementOf', () => {
  it('keys unpaid on handleOption.pay, the only CREATED-only flag on the customer payload', () => {
    expect(settlementOf({ handleOption: { pay: true, cancel: true }, orderStatusText: 'UNPAID' })).toBe('unpaid');
    expect(settlementOf({ handleOption: { refund: true }, orderStatusText: 'PAID' })).toBe('paid');
    expect(settlementOf({ handleOption: { confirm: true, aftersale: true }, orderStatusText: 'SHIPPED' })).toBe('paid');
  });
  it('a cancelled order is neither paid nor waiting', () => {
    expect(settlementOf({ handleOption: { delete: true, rebuy: true }, orderStatusText: 'CANCELLED' })).toBe('cancelled');
    expect(settlementOf({ handleOption: { delete: true }, orderStatusText: 'SYSTEM CANCELLED' })).toBe('cancelled');
  });
  it('no handleOption ⇒ unknown, never a guess', () => {
    expect(settlementOf(null)).toBe('unknown');
    expect(settlementOf({ orderStatusText: 'PAID' })).toBe('unknown');
  });
});
