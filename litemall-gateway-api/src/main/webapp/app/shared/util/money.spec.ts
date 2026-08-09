import { EURO, money, moneyAmount, moneyParts } from './money';

describe('money (Wave-24 EUR formatter)', () => {
  it('formats 2dp with the € symbol', () => {
    expect(money(12.3)).toBe('€12.30');
    expect(money(0)).toBe('€0.00');
    expect(money('7.5')).toBe('€7.50');
  });

  it('groups thousands', () => {
    expect(money(1234.56)).toBe('€1,234.56');
    expect(moneyAmount(9876543.2)).toBe('9,876,543.20');
  });

  it('is safe on null/undefined/NaN', () => {
    expect(money(null)).toBe('€0.00');
    expect(money(undefined)).toBe('€0.00');
    expect(money(Number.NaN)).toBe('€0.00');
    expect(money('not-a-number')).toBe('€0.00');
  });

  it('splits int/cents for the superscript card price', () => {
    expect(moneyParts(129.99)).toEqual({ int: '129', cents: '99' });
    expect(moneyParts(5)).toEqual({ int: '5', cents: '00' });
    expect(moneyParts(null)).toEqual({ int: '0', cents: '00' });
  });

  it('exports the symbol for renders that compose it themselves', () => {
    expect(EURO).toBe('€');
  });
});
