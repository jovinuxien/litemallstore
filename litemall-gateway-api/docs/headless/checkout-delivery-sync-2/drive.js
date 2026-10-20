// Headless real-click acceptance for checkout delivery sync pass 2 (spec §3 cases 1–7).
const puppeteer = require('puppeteer-core');
const BASE = process.env.BASE || 'http://localhost:4310';
const CHROME = '/usr/bin/google-chrome';
const results = [];
const fail = (name, msg) => { results.push({ name, ok: false, msg }); console.log('  ✗', name, '—', msg); };
const pass = (name, msg = '') => { results.push({ name, ok: true, msg }); console.log('  ✓', name, msg); };
const assert = (name, cond, msg) => (cond ? pass(name, msg) : fail(name, msg));
const sleep = ms => new Promise(r => setTimeout(r, ms));
const post = (p, body) => fetch(BASE + p, { method: 'POST', body: JSON.stringify(body), headers: { 'content-type': 'application/json' } });
const getLog = async () => (await fetch(BASE + '/__log')).json();

const SE = { id: 5, name: 'Anna Svensson', tel: '+46701234567', province: 'Skåne', city: 'Malmö', county: '', addressDetail: 'Storgatan 1', postalCode: '21111', countryCode: 'SE', isDefault: true };
const DE = { id: 7, name: 'Max Muster', tel: '+491701234567', province: 'Bayern', city: 'München', county: '', addressDetail: 'Hauptstr. 2', postalCode: '80331', countryCode: 'DE', isDefault: true };
const LEGACY = { id: 6, name: 'Old Entry', tel: '+46701234567', province: 'Stockholm', city: 'Stockholm', county: '', addressDetail: 'Gamla vägen 3', postalCode: '11122', isDefault: false };
const CART = [{ id: 1, goodsId: '10000001', goodsSn: 'cj_1', goodsName: 'Garden lamp', productId: 1, price: 20, number: 1, specifications: [], checked: true, picUrl: '', source: 'cj', vid: '123456789012345678' }];
const LABELS = {
  en: { toPayment: 'Continue to payment', toReview: 'Continue to review', place: 'Place order' },
  sv: { toPayment: 'Fortsätt till betalning', toReview: 'Fortsätt till granskning', place: 'Lägg order' },
  da: { toPayment: 'Fortsæt til betaling', toReview: 'Fortsæt til gennemsyn', place: 'Afgiv ordre' },
};

// --- page helpers (run in the browser) ---------------------------------------------------
const H = {
  countrySelect: () => Array.from(document.querySelectorAll('select')).find(s => Array.from(s.options).some(o => o.value === 'SE')) || null,
  dial: () => { const b = document.querySelector('.input-group button[aria-expanded]'); return b ? (b.textContent.match(/\+\d+/) || [null])[0] : null; },
  region: () => document.querySelector('input[autocomplete=address-level1]'),
  deliveringTo: () => { const d = document.querySelector('[data-delivering-to]'); return d ? d.textContent : null; },
  note: () => { const d = document.querySelector('[data-delivery-note]'); return d ? d.textContent : null; },
  cards: () => Array.from(document.querySelectorAll('.lm-address-card:not(.lm-address-card--new)')).map(c => c.textContent),
};
const inPage = (page, fn, ...args) => page.evaluate(new Function('H', `const h={${Object.entries(H).map(([k, v]) => `${k}:${v.toString()}`).join(',')}}; return (${fn.toString()})(h, ...arguments[1]);`), null, args);
const setSelect = (page, value) => inPage(page, (h, v) => { const s = h.countrySelect(); const setter = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set; setter.call(s, v); s.dispatchEvent(new Event('change', { bubbles: true })); return s.value; }, value);
const typeRegion = (page, text) => inPage(page, (h, v) => { const i = h.region(); const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set; i.focus(); setter.call(i, v); i.dispatchEvent(new Event('input', { bubbles: true })); return i.value; }, text);
const clickText = async (page, text) => page.evaluate(t => {
  const b = Array.from(document.querySelectorAll('button')).find(x => x.textContent.trim().toLowerCase() === t.toLowerCase() && !x.disabled);
  if (!b) return false; b.click(); return true;
}, text);
const waitFor = async (page, fn, args = [], timeout = 8000) => { const t0 = Date.now(); for (;;) { const v = await inPage(page, fn, ...args); if (v) return v; if (Date.now() - t0 > timeout) return null; await sleep(150); } };

async function openCheckout(browser, { languages, siteLang, addresses, enabled }) {
  await post('/__scenario', { addresses, languages: enabled || ['en', 'sv', 'da'] });
  // A fresh context per run: the lm_lang cookie and storage must not leak between cases.
  const ctx = await (browser.createBrowserContext ? browser.createBrowserContext() : browser.createIncognitoBrowserContext());
  const page = await ctx.newPage();
  page.__ctx = ctx;
  const errors = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push('console: ' + m.text()); });
  await page.evaluateOnNewDocument((langs, cart) => {
    Object.defineProperty(navigator, 'languages', { get: () => langs });
    Object.defineProperty(navigator, 'language', { get: () => langs[0] });
    sessionStorage.setItem('customerToken', 'test-token');
    localStorage.setItem('cart', JSON.stringify(cart));
    localStorage.setItem('lm_consent', 'denied');
  }, languages, CART);
  if (siteLang) await page.setCookie({ name: 'lm_lang', value: siteLang, url: BASE });
  await page.goto(BASE + '/checkout', { waitUntil: 'load' });
  await waitFor(page, h => !!document.querySelector('#lm-step-1 .lm-step__continue button'));
  await sleep(600); // address list + site-config settle
  return { page, errors };
}

async function placeOrder(page, L) {
  await waitFor(page, h => { const b = document.querySelector('#lm-step-1 .lm-step__continue button'); return b && !b.disabled; });
  await page.evaluate(() => document.querySelector('#lm-step-1 .lm-step__continue button').click());
  await sleep(300);
  if (!(await clickText(page, L.toReview))) return 'no review button';
  await sleep(300);
  const t0 = Date.now();
  while (Date.now() - t0 < 6000) { if (await clickText(page, L.place)) break; await sleep(200); }
  await sleep(1200);
  return 'placed';
}

const profiles = [
  { tag: 'en', languages: ['en'], siteLang: null, L: LABELS.en },
  { tag: 'sv-SE', languages: ['sv-SE', 'sv', 'en'], siteLang: 'sv', L: LABELS.sv },
  { tag: 'da', languages: ['da'], siteLang: 'da', L: LABELS.da },
  { tag: 'de-DE', languages: ['de-DE', 'de', 'en'], siteLang: null, L: LABELS.en },
];

(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox', '--disable-gpu'] });
  let pageErrors = [];
  const run = async (name, opts, fn) => {
    const { page, errors } = await openCheckout(browser, opts);
    try { await fn(page); } catch (e) { fail(name, 'threw ' + e.message); }
    pageErrors.push(...errors.map(e => `${name}: ${e}`));
    await page.close();
    await page.__ctx.close();
  };

  for (const P of profiles) {
    console.log(`\n== profile ${P.tag}`);
    // 1. saved address WITH country ⇒ read-only line, no select, submit carries it
    await run(`[${P.tag}] 1 saved-with-country`, { ...P, addresses: [SE] }, async page => {
      const line = await waitFor(page, h => h.deliveringTo());
      assert(`[${P.tag}] 1 read-only line`, line && line.includes('Sweden'), String(line));
      assert(`[${P.tag}] 1 no select`, (await inPage(page, h => h.countrySelect())) === null, 'country select absent');
      const cards = await inPage(page, h => h.cards());
      assert(`[${P.tag}] 1 card shows country`, cards[0] && cards[0].includes('Sweden'), cards[0]);
      const r = await placeOrder(page, P.L);
      const log = await getLog();
      const submit = log.find(e => e.path === '/srv/order/submit');
      assert(`[${P.tag}] 1 submit countryCode`, r === 'placed' && submit && submit.body.countryCode === 'SE', r + ' ' + JSON.stringify(submit && submit.body));
      assert(`[${P.tag}] 1 no address write-back`, !log.some(e => e.path === '/srv/address/save'), 'no addressSave');
    });
    // 2. legacy address WITHOUT country ⇒ select, pick DE ⇒ addressSave with DE before submit
    await run(`[${P.tag}] 2 legacy-no-country`, { ...P, addresses: [LEGACY] }, async page => {
      const sel = await waitFor(page, h => !!h.countrySelect());
      assert(`[${P.tag}] 2 select shown`, !!sel, 'select present');
      const v0 = await inPage(page, h => h.countrySelect().value);
      assert(`[${P.tag}] 2 select starts empty`, v0 === '', `value='${v0}'`);
      assert(`[${P.tag}] 2 no read-only line`, (await inPage(page, h => h.deliveringTo())) === null, 'absent');
      await setSelect(page, 'DE');
      const r = await placeOrder(page, P.L);
      const log = await getLog();
      const saveIdx = log.findIndex(e => e.path === '/srv/address/save');
      const subIdx = log.findIndex(e => e.path === '/srv/order/submit');
      const save = log[saveIdx];
      assert(`[${P.tag}] 2 write-back DE before submit`, r === 'placed' && saveIdx >= 0 && subIdx > saveIdx && save.body.countryCode === 'DE' && save.body.id === 6, JSON.stringify({ saveIdx, subIdx, body: save && save.body }));
      assert(`[${P.tag}] 2 submit DE`, log[subIdx] && log[subIdx].body.countryCode === 'DE', JSON.stringify(log[subIdx] && log[subIdx].body));
    });
    // 3. DE address → country-less address ⇒ Country empty
    await run(`[${P.tag}] 3 reset-on-countryless`, { ...P, addresses: [DE, LEGACY] }, async page => {
      const line = await waitFor(page, h => h.deliveringTo());
      assert(`[${P.tag}] 3 starts on Germany`, line && line.includes('Germany'), String(line));
      await page.evaluate(() => document.querySelectorAll('.lm-address-card:not(.lm-address-card--new)')[1].click());
      await sleep(300);
      const v = await inPage(page, h => { const s = h.countrySelect(); return s ? s.value : 'NO SELECT'; });
      assert(`[${P.tag}] 3 Country empty after pick`, v === '', `value='${v}'`);
      await page.evaluate(() => document.querySelectorAll('.lm-address-card:not(.lm-address-card--new)')[0].click());
      await sleep(300);
      const back = await inPage(page, h => h.deliveringTo());
      assert(`[${P.tag}] 3 back to read-only Germany`, back && back.includes('Germany') && (await inPage(page, h => h.countrySelect())) === null, String(back));
    });
    // 6. new address, Country cleared, type "Bayern" and pick ⇒ Germany, +49
    await run(`[${P.tag}] 6 region-names-country`, { ...P, addresses: [] }, async page => {
      await waitFor(page, h => !!h.countrySelect());
      await setSelect(page, '');
      await typeRegion(page, 'Bay');
      const opt = await waitFor(page, h => { const o = Array.from(document.querySelectorAll('div[role=listbox] [role=option]')).find(x => x.textContent === 'Bayern · Germany'); return o ? o.textContent : null; });
      assert(`[${P.tag}] 6 labelled option`, opt === 'Bayern · Germany', String(opt));
      await page.evaluate(() => Array.from(document.querySelectorAll('div[role=listbox] [role=option]')).find(x => x.textContent === 'Bayern · Germany').click());
      await sleep(300);
      const st = await inPage(page, h => ({ c: h.countrySelect().value, d: h.dial(), r: h.region().value }));
      assert(`[${P.tag}] 6 Country Germany, +49, region Bayern`, st.c === 'DE' && st.d === '+49' && st.r === 'Bayern', JSON.stringify(st));
      // typed exact unique match also fills (after clearing)
      await setSelect(page, '');
      await typeRegion(page, 'skane');
      await sleep(200);
      const st2 = await inPage(page, h => ({ c: h.countrySelect().value, d: h.dial(), r: h.region().value }));
      assert(`[${P.tag}] 6b typed "skane" ⇒ Sweden, Skåne`, st2.c === 'SE' && st2.d === '+46' && st2.r === 'Skåne', JSON.stringify(st2));
    });
  }

  console.log('\n== single-profile cases');
  // 4. plain en browser ⇒ Country empty, +1, no note
  await run('4 plain-en', { languages: ['en'], siteLang: null, addresses: [] }, async page => {
    await waitFor(page, h => !!h.countrySelect());
    const st = await inPage(page, h => ({ c: h.countrySelect().value, d: h.dial(), n: h.note() }));
    assert('4 Country empty, dial +1, no note', st.c === '' && st.d === '+1' && st.n === null, JSON.stringify(st));
  });
  // 5. site language sv on an en-US browser ⇒ Sweden
  await run('5 site-sv', { languages: ['en-US', 'en'], siteLang: 'sv', addresses: [] }, async page => {
    await waitFor(page, h => !!h.countrySelect());
    const st = await inPage(page, h => ({ c: h.countrySelect().value, d: h.dial(), lang: document.documentElement.lang }));
    assert('5 Country Sweden, +46 (site sv)', st.c === 'SE' && st.d === '+46' && st.lang === 'sv', JSON.stringify(st));
  });
  // 5b. confident non-delivery guess (fi-FI) ⇒ blank Country explained
  await run('5b fi-browser', { languages: ['fi-FI', 'fi'], siteLang: null, addresses: [] }, async page => {
    await waitFor(page, h => !!h.countrySelect());
    const st = await inPage(page, h => ({ c: h.countrySelect().value, d: h.dial(), n: h.note() }));
    assert('5b Country empty, +358, note explains', st.c === '' && st.d === '+358' && st.n && /Finland/.test(st.n), JSON.stringify(st));
  });
  // 7. Danish browser ⇒ Denmark, +45, 5 regions
  await run('7 danish', { languages: ['da-DK', 'da'], siteLang: 'da', addresses: [] }, async page => {
    await waitFor(page, h => !!h.countrySelect());
    const st = await inPage(page, h => ({ c: h.countrySelect().value, d: h.dial() }));
    assert('7 Country Denmark, +45', st.c === 'DK' && st.d === '+45', JSON.stringify(st));
    await page.evaluate(() => { const i = document.querySelector('input[autocomplete=address-level1]'); i.focus(); i.dispatchEvent(new Event('focus')); });
    const n = await waitFor(page, h => { const o = document.querySelectorAll('div[role=listbox] [role=option]'); return o.length || null; });
    const names = await page.evaluate(() => Array.from(document.querySelectorAll('div[role=listbox] [role=option]')).map(o => o.textContent));
    assert('7 five Danish regions, unlabelled', n === 5 && names.includes('Sjælland') && !names.some(x => x.includes('·')), JSON.stringify(names));
  });

  await browser.close();
  const failed = results.filter(r => !r.ok);
  console.log(`\nRESULT: ${results.length - failed.length}/${results.length} assertions passed, ${pageErrors.length} page/console errors`);
  pageErrors.forEach(e => console.log('  PAGE ERROR:', e));
  process.exit(failed.length || pageErrors.length ? 1 : 0);
})();
