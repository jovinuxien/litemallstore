import {
  __applyCurrencyAnswer,
  __resetCurrency,
  CURRENCY_COOKIE,
  currencyRenderKey,
  currencySnapshot,
  currentCurrency,
  currentRate,
  initCurrency,
  isConverted,
  setCurrency,
  subscribeCurrency,
} from './currency';

const ALL = {
  country: 'SE',
  suggested: 'SEK',
  enabled: ['EUR', 'SEK', 'DKK', 'NOK', 'GBP'],
  rates: { SEK: 11.0125, DKK: 7.4641, NOK: 11.718, GBP: 0.8785 },
  asOf: '2026-11-13',
};
const envelope = (data: unknown) => ({ ok: true, json: () => Promise.resolve({ errno: 0, data }) });
const setCookie = (v: string) => {
  document.cookie = `${CURRENCY_COOKIE}=${v}; Path=/`;
};
const setQuery = (q: string) => window.history.replaceState({}, '', `/${q}`);

const realFetch = global.fetch;
beforeEach(() => {
  __resetCurrency();
  setQuery('');
});
afterEach(() => {
  global.fetch = realFetch;
});

describe('display-currency store', () => {
  it('starts on EUR with nothing to choose', () => {
    expect(currencySnapshot()).toEqual({ currency: 'EUR', enabled: ['EUR'], rates: {}, asOf: null, country: null });
    expect(isConverted()).toBe(false);
    expect(currentRate()).toBe(1);
  });

  it('follows the edge suggestion for the visitor country', () => {
    __applyCurrencyAnswer(ALL);
    expect(currentCurrency()).toBe('SEK');
    expect(currentRate()).toBe(11.0125);
    expect(isConverted()).toBe(true);
    expect(currencySnapshot().enabled).toEqual(['EUR', 'SEK', 'DKK', 'NOK', 'GBP']);
    expect(currencySnapshot().asOf).toBe('2026-11-13');
  });

  it('a euro-country visitor stays on EUR but may choose', () => {
    __applyCurrencyAnswer({ ...ALL, country: 'DE', suggested: 'EUR' });
    expect(currentCurrency()).toBe('EUR');
    expect(currencySnapshot().enabled.length).toBe(5);
  });

  it('precedence: ?currency= beats the cookie, the cookie beats the country', () => {
    setCookie('DKK');
    __applyCurrencyAnswer(ALL);
    expect(currentCurrency()).toBe('DKK');

    setQuery('?currency=gbp');
    __applyCurrencyAnswer(ALL);
    expect(currentCurrency()).toBe('GBP');
  });

  it('an explicit EUR choice holds against a Swedish IP', () => {
    setCookie('EUR');
    __applyCurrencyAnswer(ALL);
    expect(currentCurrency()).toBe('EUR');
  });

  it('ignores a preference for a currency that is not on offer', () => {
    setCookie('NOK');
    setQuery('?currency=XYZ');
    __applyCurrencyAnswer({ ...ALL, enabled: ['EUR', 'SEK'], rates: { SEK: 11 } });
    expect(currentCurrency()).toBe('SEK'); // falls through to the suggestion
    __applyCurrencyAnswer({ ...ALL, suggested: 'NOK', enabled: ['EUR', 'SEK'], rates: { SEK: 11 } });
    expect(currentCurrency()).toBe('EUR'); // suggestion not on offer either
  });

  it('a currency without a usable rate is not enabled — never a made-up conversion', () => {
    __applyCurrencyAnswer({ ...ALL, rates: { SEK: 0, DKK: 'x', NOK: 11.7 } });
    expect(currencySnapshot().enabled).toEqual(['EUR', 'NOK']);
    expect(currentCurrency()).toBe('EUR'); // SEK was suggested but has no rate
  });

  it('degrades to EUR-only on an empty, malformed or missing answer', () => {
    for (const bad of [null, undefined, {}, { enabled: 'SEK' }, { enabled: ['SEK'], rates: null }, { enabled: ['EUR'] }]) {
      __applyCurrencyAnswer(ALL);
      __applyCurrencyAnswer(bad as never);
      expect(currencySnapshot().enabled).toEqual(['EUR']);
      expect(currentCurrency()).toBe('EUR');
      expect(currencySnapshot().asOf).toBeNull();
    }
  });

  it('setCurrency remembers the choice in the cookie and notifies subscribers', () => {
    __applyCurrencyAnswer(ALL);
    const seen: string[] = [];
    const off = subscribeCurrency(() => seen.push(currentCurrency()));
    expect(setCurrency('GBP')).toBe('GBP');
    expect(document.cookie).toContain(`${CURRENCY_COOKIE}=GBP`);
    setCurrency('GBP'); // no change, no notification
    off();
    expect(seen).toEqual(['GBP']);
    __applyCurrencyAnswer(ALL); // the next page load honours it
    expect(currentCurrency()).toBe('GBP');
  });

  it('setCurrency refuses a currency that is not on offer', () => {
    __applyCurrencyAnswer({ ...ALL, enabled: ['EUR', 'SEK'], rates: { SEK: 11 } });
    expect(setCurrency('GBP')).toBe('EUR');
  });

  it('the render key moves with the currency and its rate, and is constant on EUR', () => {
    const off = currencyRenderKey();
    __applyCurrencyAnswer({ ...ALL, suggested: 'EUR' });
    expect(currencyRenderKey()).toBe(off); // rates arriving do not disturb a euro visitor
    setCurrency('SEK');
    const sek = currencyRenderKey();
    expect(sek).not.toBe(off);
    __applyCurrencyAnswer({ ...ALL, rates: { ...ALL.rates, SEK: 11.2 } });
    expect(currencyRenderKey()).not.toBe(sek);
  });
});

describe('initCurrency (boot)', () => {
  it('applies the edge answer and keeps it for the next page load', async () => {
    global.fetch = jest.fn().mockResolvedValue(envelope(ALL)) as never;
    await initCurrency();
    expect(global.fetch).toHaveBeenCalledWith('/auth/currency', expect.anything());
    expect(currentCurrency()).toBe('SEK');
    expect(JSON.parse(window.localStorage.getItem('lm_cur_cache') as string).answer.suggested).toBe('SEK');
  });

  it('paints from the cached answer first, then the live one replaces it', async () => {
    window.localStorage.setItem('lm_cur_cache', JSON.stringify({ savedAt: Date.now(), answer: ALL }));
    let release: (v: unknown) => void = () => undefined;
    global.fetch = jest.fn().mockReturnValue(new Promise(r => (release = r))) as never;
    const boot = initCurrency();
    expect(currentCurrency()).toBe('SEK'); // before the edge has answered
    release(envelope({ ...ALL, country: 'DK', suggested: 'DKK' }));
    await boot;
    expect(currentCurrency()).toBe('DKK');
  });

  it('ignores a cache older than a day', async () => {
    window.localStorage.setItem('lm_cur_cache', JSON.stringify({ savedAt: Date.now() - 25 * 3600 * 1000, answer: ALL }));
    global.fetch = jest.fn().mockReturnValue(new Promise(() => undefined)) as never;
    void initCurrency();
    expect(currentCurrency()).toBe('EUR');
  });

  it('stays on EUR when the edge is down, an old edge 404s, or the envelope is an error', async () => {
    for (const answer of [Promise.reject(new Error('offline')), Promise.resolve({ ok: false, json: () => Promise.resolve({}) }), Promise.resolve({ ok: true, json: () => Promise.resolve({ errno: 501 }) })]) {
      __resetCurrency();
      global.fetch = jest.fn().mockReturnValue(answer) as never;
      await initCurrency();
      expect(currentCurrency()).toBe('EUR');
      expect(currencySnapshot().enabled).toEqual(['EUR']);
    }
  });

  it('clears the cache when the feature is switched off', async () => {
    window.localStorage.setItem('lm_cur_cache', JSON.stringify({ savedAt: Date.now(), answer: ALL }));
    global.fetch = jest.fn().mockResolvedValue(envelope({ country: 'SE', suggested: 'EUR', enabled: ['EUR'], rates: {}, asOf: null })) as never;
    await initCurrency();
    expect(currentCurrency()).toBe('EUR');
    expect(window.localStorage.getItem('lm_cur_cache')).toBeNull();
  });
});
