// Headless real-click acceptance for the display currency, stage A
// (litemall-gateway-api/docs/spec-display-currency.md). Runs the BUILT bundle against stub.js.
const puppeteer = require('puppeteer-core');
const BASE = process.env.BASE || 'http://localhost:4312';
const CHROME = '/usr/bin/google-chrome';
const results = [];
const assert = (name, cond, msg = '') => { results.push({ name, ok: !!cond }); console.log(cond ? '  ✓' : '  ✗', name, cond ? '' : '— ' + msg); };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const post = (p, body) => fetch(BASE + p, { method: 'POST', body: JSON.stringify(body), headers: { 'content-type': 'application/json' } });

const RATES = { SEK: 11.0125, DKK: 7.4641, NOK: 11.718, GBP: 0.8785 };
const answer = (country, suggested, enabled = ['EUR', 'SEK', 'DKK', 'NOK', 'GBP']) => ({ country, suggested, enabled, rates: Object.fromEntries(enabled.filter(c => c !== 'EUR').map(c => [c, RATES[c]])), asOf: '2026-11-13' });
const SE_ADDR = { id: 5, name: 'Anna Svensson', tel: '+46701234567', province: 'Skåne', city: 'Malmö', county: '', addressDetail: 'Storgatan 1', postalCode: '21111', countryCode: 'SE', isDefault: true };
const CART = [{ id: 1, goodsId: '10000001', goodsSn: 'cj_1', goodsName: 'Garden lamp', productId: 1, price: 20, number: 1, specifications: [], checked: true, picUrl: '', source: 'cj', vid: '123456789012345678' }];

// €20.00 goods, €6.93 shipping, €26.93 charged — what each market must read.
const MARKETS = [
  { country: 'SE', cur: 'SEK', price: '220 kr', ship: '76 kr', total: '297 kr' },
  { country: 'DK', cur: 'DKK', price: '149 kr', ship: '52 kr', total: '201 kr' },
  { country: 'NO', cur: 'NOK', price: '234 kr', ship: '81 kr', total: '316 kr' },
  { country: 'GB', cur: 'GBP', price: '£17.57', ship: '£6.09', total: '£23.66' },
];

// innerText with every whitespace run (incl. NBSP / narrow NBSP) folded to one space.
const text = page => page.evaluate(() => document.body.innerText.replace(/\s+/g, ' '));
const waitText = async (page, needle, timeout = 8000) => { const t0 = Date.now(); for (;;) { const t = await text(page); if (t.includes(needle)) return t; if (Date.now() - t0 > timeout) return t; await sleep(150); } };
const clickButton = (page, label) => page.evaluate(l => { const b = Array.from(document.querySelectorAll('button')).find(x => x.textContent.trim() === l); if (!b) return false; b.click(); return true; }, label);

(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox', '--disable-gpu'] });
  const pageErrors = [];
  const run = async (tag, scenario, opts, fn) => {
    await post('/__scenario', { addresses: [SE_ADDR], languages: ['en', 'sv', 'da'], ...scenario });
    // One context per case: lm_cur / lm_lang cookies and the rates cache must not leak.
    const ctx = await (browser.createBrowserContext ? browser.createBrowserContext() : browser.createIncognitoBrowserContext());
    const page = await ctx.newPage();
    await page.setViewport({ width: 1280, height: 900 });
    page.on('pageerror', e => pageErrors.push(`[${tag}] pageerror: ${e.message}`));
    page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) pageErrors.push(`[${tag}] console: ${m.text()}`); });
    await page.evaluateOnNewDocument((langs, cart) => {
      Object.defineProperty(navigator, 'languages', { get: () => langs });
      Object.defineProperty(navigator, 'language', { get: () => langs[0] });
      sessionStorage.setItem('customerToken', 'test-token');
      localStorage.setItem('cart', JSON.stringify(cart));
      localStorage.setItem('lm_consent', 'denied');
    }, opts.languages || ['en'], CART);
    if (opts.siteLang) await page.setCookie({ name: 'lm_lang', value: opts.siteLang, url: BASE });
    const go = async path => { await page.goto(BASE + path, { waitUntil: 'load' }); await sleep(700); };
    try { await fn(page, go); } catch (e) { assert(`[${tag}] case threw`, false, e.message); }
    await ctx.close();
  };

  for (const M of MARKETS) {
    console.log(`\n== visitor from ${M.country} → ${M.cur}`);
    await run(M.country, { currency: answer(M.country, M.cur) }, {}, async (page, go) => {
      await go('/product/10000001');
      let t = await waitText(page, M.price);
      assert(`[${M.country}] product page price is ${M.price}`, t.includes(M.price), t.slice(0, 300));
      assert(`[${M.country}] product page says what is charged`, t.includes('Charged in euros: €20.00'));

      await go('/cart');
      t = await waitText(page, M.price);
      assert(`[${M.country}] cart shows ${M.price}`, t.includes(M.price));
      assert(`[${M.country}] cart total carries the euro charge`, t.includes('Charged in euros: €20.00'));
      assert(`[${M.country}] cart shows no bare euro line price`, !/€20\.00(?! ?$)/.test(t.replace(/Charged in euros: €20\.00/g, '')));

      await go('/checkout');
      t = await waitText(page, 'You pay');
      assert(`[${M.country}] checkout goods + shipping in ${M.cur}`, t.includes(M.price) && t.includes(M.ship), t.slice(0, 400));
      assert(`[${M.country}] checkout estimate row "≈ ${M.total}"`, t.includes(`Total in ${M.cur} (estimate)`) && t.includes(`≈ ${M.total}`));
      assert(`[${M.country}] checkout bold total is the euro charge`, /You pay ?€26\.93/.test(t), t.slice(0, 400));
      assert(`[${M.country}] no currency selector on checkout`, !(await page.evaluate(() => !!document.querySelector('.lm-cur'))));

      await go('/order/1');
      t = await waitText(page, '€26.93');
      assert(`[${M.country}] order record stays in euros`, t.includes('€26.93') && t.includes('€20.00') && t.includes('€6.93'));
      assert(`[${M.country}] order record shows no converted amount`, !t.includes(M.total) && !t.includes(M.price));
    });
  }

  console.log('\n== visitor from DE → EUR');
  await run('DE', { currency: answer('DE', 'EUR') }, {}, async (page, go) => {
    await go('/product/10000001');
    let t = await waitText(page, '€20.00');
    assert('[DE] product page price is €20.00', t.includes('€20.00') && !/\d kr\b/.test(t));
    assert('[DE] no "charged in euros" note (it IS the charge)', !t.includes('Charged in euros'));
    assert('[DE] selector is offered', await page.evaluate(() => !!document.querySelector('.lm-cur--footer')));
    await go('/checkout');
    t = await waitText(page, '€26.93');
    assert('[DE] checkout has the plain total row, no estimate row', t.includes('€26.93') && !t.includes('(estimate)') && !t.includes('You pay'));

    // the visitor picks kronor; it sticks across a reload
    await go('/cart');
    assert('[DE] click SEK in the footer selector', await clickButton(page, 'SEK'));
    t = await waitText(page, '220 kr');
    assert('[DE→SEK] cart re-renders in kronor without a reload', t.includes('220 kr') && t.includes('Charged in euros: €20.00'));
    await go('/product/10000001');
    t = await waitText(page, '220 kr');
    assert('[DE→SEK] choice survives a page load (cookie beats country)', t.includes('220 kr'));
    assert('[DE→SEK] cookie lm_cur=SEK', (await page.cookies()).some(c => c.name === 'lm_cur' && c.value === 'SEK'));
    await go('/cart');
    await clickButton(page, 'EUR');
    t = await waitText(page, '€20.00');
    assert('[DE→SEK→EUR] back to euros', t.includes('€20.00') && !t.includes('220 kr') && !t.includes('Charged in euros'));
  });

  console.log('\n== Swedish visitor who chose EUR / link override');
  await run('SE+cookie', { currency: answer('SE', 'SEK') }, {}, async (page, go) => {
    await page.setCookie({ name: 'lm_cur', value: 'EUR', url: BASE });
    await go('/product/10000001');
    const t = await waitText(page, '€20.00');
    assert('[SE, cookie EUR] explicit choice beats the country', t.includes('€20.00') && !t.includes('220 kr'));
    await go('/product/10000001?currency=gbp');
    const g = await waitText(page, '£17.57');
    assert('[SE, ?currency=gbp] the link beats the cookie', g.includes('£17.57'));
  });

  console.log('\n== site language decides the separators');
  await run('SE sv', { currency: answer('SE', 'SEK') }, { languages: ['sv-SE', 'sv'], siteLang: 'sv' }, async (page, go) => {
    await go('/checkout');
    const t = await waitText(page, 'Du betalar');
    assert('[SE sv] Swedish strings + Swedish euro writing', t.includes('Totalt i SEK (uppskattning)') && t.includes('≈ 297 kr') && /Du betalar ?26,93 €/.test(t), t.slice(0, 400));
  });
  await run('DK da', { currency: answer('DK', 'DKK') }, { languages: ['da'], siteLang: 'da' }, async (page, go) => {
    await go('/cart');
    const t = await waitText(page, 'Opkræves i euro');
    assert('[DK da] Danish strings', t.includes('149 kr') && t.includes('Opkræves i euro: 20,00 €'), t.slice(0, 400));
  });

  console.log('\n== fail honest: no rates ⇒ euros, no selector');
  for (const [tag, currency] of [['old edge (404)', 'missing'], ['edge down (500)', 'down'], ['feature off', answer('SE', 'EUR', ['EUR'])], ['rates stale (edge sends EUR only)', { country: 'SE', suggested: 'EUR', enabled: ['EUR'], rates: {}, asOf: null }]]) {
    await run(tag, { currency }, {}, async (page, go) => {
      await go('/product/10000001');
      const t = await waitText(page, '€20.00');
      assert(`[${tag}] Swedish visitor sees €20.00`, t.includes('€20.00') && !/\d kr\b/.test(t) && !t.includes('Charged in euros'));
      assert(`[${tag}] no selector`, !(await page.evaluate(() => !!document.querySelector('.lm-cur'))));
    });
  }

  console.log('\n== operator enabled only SEK');
  await run('DK, SEK only', { currency: answer('DK', 'EUR', ['EUR', 'SEK']) }, {}, async (page, go) => {
    await go('/cart');
    const t = await waitText(page, '€20.00');
    assert('[DK, SEK only] Danish visitor stays on euros', t.includes('€20.00') && !/\d kr\b/.test(t));
    const offered = await page.evaluate(() => Array.from(document.querySelectorAll('.lm-cur--footer button')).map(b => b.textContent.trim()));
    assert('[DK, SEK only] selector offers exactly EUR + SEK', offered.join(',') === 'EUR,SEK', offered.join(','));
  });

  await browser.close();
  const failed = results.filter(r => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} assertions passed, ${pageErrors.length} page errors`);
  pageErrors.forEach(e => console.log('  page error:', e));
  process.exit(failed.length || pageErrors.length ? 1 : 0);
})();
