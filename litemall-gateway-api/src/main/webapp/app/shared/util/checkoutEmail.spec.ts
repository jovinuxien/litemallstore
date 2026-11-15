import { emailSaveOutcome, isEmailRequiredRefusal, isValidCheckoutEmail } from './checkoutEmail';

describe('isValidCheckoutEmail (mirrors the server rule)', () => {
  it('accepts ordinary addresses, trimmed', () => {
    expect(isValidCheckoutEmail('jane@example.com')).toBe(true);
    expect(isValidCheckoutEmail('  jane.doe+shop@mail.example.se  ')).toBe(true);
  });

  it('refuses what the server refuses', () => {
    expect(isValidCheckoutEmail('')).toBe(false);
    expect(isValidCheckoutEmail(null)).toBe(false);
    expect(isValidCheckoutEmail('jane@example')).toBe(false);
    expect(isValidCheckoutEmail('jane example@x.com')).toBe(false);
    // The old form check (\S+@\S+\.\S+) let this one through; the server does not.
    expect(isValidCheckoutEmail('a@b@c.d')).toBe(false);
    expect(isValidCheckoutEmail(`${'a'.repeat(120)}@example.com`)).toBe(false);
  });
});

describe('emailSaveOutcome', () => {
  it('errno 0 is a save', () => {
    expect(emailSaveOutcome({ errno: 0, errmsg: 'ok' })).toEqual({ ok: true });
  });

  it('a refusal carries the server’s own words', () => {
    expect(emailSaveOutcome({ errno: 402, errmsg: 'invalid email address' })).toEqual({
      ok: false,
      reason: 'invalid email address',
    });
  });

  it('an answer with nothing in it is a failure without a reason', () => {
    expect(emailSaveOutcome(null)).toEqual({ ok: false, reason: null });
    expect(emailSaveOutcome({})).toEqual({ ok: false, reason: null });
  });
});

describe('isEmailRequiredRefusal', () => {
  it('recognises the order service refusal', () => {
    expect(
      isEmailRequiredRefusal('Add an email address to place this order — we send your order confirmation and tracking updates there.')
    ).toBe(true);
  });

  it('does not fire on other submit refusals', () => {
    expect(isEmailRequiredRefusal('Your cart is empty — add at least one item before placing an order.')).toBe(false);
    expect(isEmailRequiredRefusal('The selected shipping address was not found — choose a valid address.')).toBe(false);
    expect(isEmailRequiredRefusal(null)).toBe(false);
  });
});
