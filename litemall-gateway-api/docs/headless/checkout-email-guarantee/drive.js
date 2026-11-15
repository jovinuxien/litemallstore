// Headless real-click acceptance for the checkout email guarantee
// (litemall-order/docs/plan-order-email-guarantee-2026-11-12.md, step 1).
const puppeteer = require('puppeteer-core');
const BASE = process.env.BASE || 'http://localhost:4311';
const CHROME = '/usr/bin/google-chrome';
const results = [];
const assert = (name, cond, msg = '') => { results.push({ name, ok: !!cond }); console.log(cond ? '  ✓' : '  ✗', name, cond ? '' : '— ' + msg); };
const sleep = ms => new Promise(r => setTimeout(r, ms));
const post = (p, body) => fetch(BASE + p, { method: 'POST', body: JSON.stringify(body), headers: { 'content-type': 'application/json' } });
const getLog = async () => (await fetch(BASE + '/__log')).json();

const REFUSAL = 'Add an email address to place this order — we send your order confirmation and tracking updates there.';
const SE = { id: 5, name: 'Anna Svensson', tel: '+46701234567', province: 'Skåne', city: 'Malmö', county: '', addressDetail: 'Storgatan 1', postalCode: '21111', countryCode: 'SE', isDefault: true };
const CART = [{ id: 1, goodsId: '10000001', goodsSn: 'cj_1', goodsName: 'Garden lamp', productId: 1, price: 20, number: 1, specifications: [], checked: true, picUrl: '', source: 'cj', vid: '123456789012345678' }];
const PROFILES = [
  { tag: 'en', languages: ['en'], siteLang: null, toReview: 'Continue to review', place: 'Place order', generic: 'Your order has not been placed.' },
  { tag: 'sv', languages: ['sv-SE', 'sv', 'en'], siteLang: 'sv', toReview: 'Fortsätt till granskning', place: 'Lägg order', generic: 'Din order har inte lagts.' },
  { tag: 'da', languages: ['da'], siteLang: 'da', toReview: 'Fortsæt til gennemsyn', place: 'Afgiv ordre', generic: 'Din ordre er ikke afgivet.' },
];

const emailInput = page => page.evaluate(() => !!document.querySelector('input[name=email]'));
const bodyText = page => page.evaluate(() => document.body.innerText);
const typeEmail = (page, value) => page.evaluate(v => {
  const i = document.querySelector('input[name=email]');
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(i, v);
  i.dispatchEvent(new Event('input', { bubbles: true }));
}, value);
const clickText = (page, text) => page.evaluate(t => {
  const b = Array.from(document.querySelectorAll('button')).find(x => x.textContent.trim().toLowerCase() === t.toLowerCase() && !x.disabled);
  if (!b) return false; b.click(); return true;
}, text);
const waitFor = async (page, fn, timeout = 8000) => { const t0 = Date.now(); for (;;) { const v = await page.evaluate(fn); if (v) return v; if (Date.now() - t0 > timeout) return null; await sleep(150); } };

async function open(browser, P, scenario) {
  await post('/__scenario', { addresses: [SE], languages: ['en', 'sv', 'da'], ...scenario });
  // One context per case: the lm_lang cookie and storage must not leak between cases.
  const ctx = await (browser.createBrowserContext ? browser.createBrowserContext() : browser.createIncognitoBrowserContext());
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push('console: ' + m.text()); });
  await page.evaluateOnNewDocument((langs, cart) => {
    Object.defineProperty(navigator, 'languages', { get: () => langs });
    Object.defineProperty(navigator, 'language', { get: () => langs[0] });
    sessionStorage.setItem('customerToken', 'test-token');
    localStorage.setItem('cart', JSON.stringify(cart));
    localStorage.setItem('lm_consent', 'denied');
  }, P.languages, CART);
  if (P.siteLang) await page.setCookie({ name: 'lm_lang', value: P.siteLang, url: BASE });
  await page.goto(BASE + '/checkout', { waitUntil: 'load' });
  await waitFor(page, () => !!document.querySelector('#lm-step-1 .lm-step__continue button'));
  await sleep(600);
  return { page, ctx, errors };
}

async function place(page, P) {
  const ready = await waitFor(page, () => { const b = document.querySelector('#lm-step-1 .lm-step__continue button'); return b && !b.disabled; });
  if (!ready) return 'step 1 not ready';
  await page.evaluate(() => document.querySelector('#lm-step-1 .lm-step__continue button').click());
  await sleep(300);
  if (!(await clickText(page, P.toReview))) return 'no review button';
  await sleep(300);
  const t0 = Date.now();
  let clicked = false;
  while (Date.now() - t0 < 6000) { if (await clickText(page, P.place)) { clicked = true; break; } await sleep(200); }
  await sleep(1500);
  return clicked ? 'clicked' : 'no place button';
}

(async () => {
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--no-sandbox', '--disable-gpu'] });
  const pageErrors = [];
  const run = async (P, scenario, fn) => {
    const { page, ctx, errors } = await open(browser, P, scenario);
    try { await fn(page); } catch (e) { assert(`[${P.tag}] case threw`, false, e.message); }
    pageErrors.push(...errors);
    await ctx.close();
  };

  for (const P of PROFILES) {
    console.log(`\n== ${P.tag}`);

    // A. account has no email; the server REFUSES the address ⇒ nothing placed, reason shown
    await run(P, { meEmail: '', profile: { errno: 402, errmsg: 'invalid email address' } }, async page => {
      assert(`[${P.tag}] A email field shown`, await emailInput(page));
      await typeEmail(page, 'anna@example.com');
      const r = await place(page, P);
      const log = await getLog();
      assert(`[${P.tag}] A profile save attempted`, r === 'clicked' && log.some(e => e.path === '/auth/profile' && e.body.email === 'anna@example.com'), r);
      assert(`[${P.tag}] A NO order submitted`, !log.some(e => e.path === '/srv/order/submit'));
      assert(`[${P.tag}] A server reason shown`, (await bodyText(page)).includes('invalid email address'));
      assert(`[${P.tag}] A delivery step reopened`, await emailInput(page));
    });

    // B. the save fails in transport ⇒ nothing placed, generic line in the site language
    await run(P, { meEmail: '', profile: 'transport' }, async page => {
      await typeEmail(page, 'anna@example.com');
      const r = await place(page, P);
      const log = await getLog();
      assert(`[${P.tag}] B NO order submitted`, r === 'clicked' && !log.some(e => e.path === '/srv/order/submit'), r);
      assert(`[${P.tag}] B generic line says nothing was placed`, (await bodyText(page)).includes(P.generic));
    });

    // C. the save lands ⇒ the order is submitted, AFTER the save
    await run(P, { meEmail: '', profile: 'ok' }, async page => {
      await typeEmail(page, 'anna@example.com');
      const r = await place(page, P);
      const log = await getLog();
      const save = log.findIndex(e => e.path === '/auth/profile' && e.body.email === 'anna@example.com');
      const submit = log.findIndex(e => e.path === '/srv/order/submit');
      assert(`[${P.tag}] C saved then submitted`, r === 'clicked' && save >= 0 && submit > save, JSON.stringify({ r, save, submit }));
    });

    // D. a malformed address the old form accepted cannot reach the button
    await run(P, { meEmail: '', profile: 'ok' }, async page => {
      await typeEmail(page, 'a@b@c.d');
      await sleep(300);
      const disabled = await page.evaluate(() => document.querySelector('#lm-step-1 .lm-step__continue button').disabled);
      assert(`[${P.tag}] D malformed address keeps the step closed`, disabled);
    });

    // E. account HAS an email here, but the order service refuses (422) ⇒ verbatim + field revealed
    await run(P, { meEmail: 'anna@example.com', submit: { status: 422, body: { success: false, message: REFUSAL } } }, async page => {
      assert(`[${P.tag}] E no email field at first`, !(await emailInput(page)));
      const r = await place(page, P);
      const log = await getLog();
      assert(`[${P.tag}] E submit attempted, no payment`, r === 'clicked' && log.some(e => e.path === '/srv/order/submit') && !log.some(e => /actions\/pay/.test(e.path)), r);
      assert(`[${P.tag}] E refusal shown verbatim`, (await bodyText(page)).includes(REFUSAL));
      assert(`[${P.tag}] E email field revealed`, await emailInput(page));
    });
  }

  await browser.close();
  const failed = results.filter(r => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} assertions passed, ${pageErrors.length} page errors`);
  pageErrors.forEach(e => console.log('  page error:', e));
  process.exit(failed.length || pageErrors.length ? 1 : 0);
})();
