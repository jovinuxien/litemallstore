// Stub edge for the checkout headless run: serves the BUILT bundle and answers /srv + /auth
// with canned envelopes. Scenario (addresses, enabled languages) is set by the driver via
// POST /__scenario; every API request is recorded for GET /__log.
const http = require('http');
const fs = require('fs');
const path = require('path');
const STATIC = process.argv[2];
const PORT = Number(process.argv[3] || 4310);
let scenario = { addresses: [], languages: ['en'] };
let log = [];
const json = (res, status, body) => { res.writeHead(status, { 'content-type': 'application/json' }); res.end(JSON.stringify(body)); };
const ok = (data) => ({ errno: 0, errmsg: 'ok', data });
const MIME = { '.js': 'application/javascript', '.css': 'text/css', '.html': 'text/html', '.json': 'application/json', '.woff': 'font/woff', '.woff2': 'font/woff2', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon' };
http.createServer((req, res) => {
  const u = new URL(req.url, 'http://x');
  let chunks = [];
  req.on('data', c => chunks.push(c));
  req.on('end', () => {
    const raw = Buffer.concat(chunks).toString('utf8');
    let body = null; try { body = raw ? JSON.parse(raw) : null; } catch { body = raw; }
    const p = u.pathname;
    if (p === '/__scenario') { scenario = { ...scenario, ...body }; log = []; return json(res, 200, { ok: true }); }
    if (p === '/__log') return json(res, 200, log);
    if (p.startsWith('/srv') || p.startsWith('/auth')) {
      log.push({ method: req.method, path: p, query: Object.fromEntries(u.searchParams), body });
      if (p === '/auth/site-config') return json(res, 200, ok({ i18nLanguages: scenario.languages, placesApiKey: null, stripePublishableKey: null, metaPixelId: null, googleClientId: null, matomoUrl: null, matomoSiteId: null, socialFacebookUrl: null, socialInstagramUrl: null, socialTiktokUrl: null, socialYoutubeUrl: null, socialXUrl: null }));
      if (p === '/auth/me') return json(res, 200, ok({ username: 'anna', nickname: 'Anna', email: 'anna@example.com', mobile: '' }));
      if (p === '/auth/profile') return json(res, 200, ok({ email: 'anna@example.com' }));
      if (p === '/srv/address/list') return json(res, 200, ok(scenario.addresses));
      if (p === '/srv/address/save') return json(res, 200, ok(body && body.id ? body.id : 99));
      if (p === '/srv/cart/items' && req.method === 'GET') return json(res, 200, ok([]));
      if (p === '/srv/cart/items') return json(res, 200, ok({}));
      if (p === '/srv/cart/checkout') return json(res, 200, ok({ goodsTotalPrice: 20, freightPrice: 6.93, taxPrice: 0, couponPrice: 0, orderTotalPrice: 26.93, actualPrice: 26.93, checkedGoodsList: [] }));
      if (p === '/srv/order/freight-quote') return json(res, 200, ok({ freightPrice: 6.93, source: 'SYSTEM_FLAT', breakdown: [], cj: { logisticName: 'CJPacket Ordinary', logisticAging: '10-15', options: [] } }));
      if (p === '/srv/goods/origin') return json(res, 200, ok({ list: [] }));
      if (p === '/srv/coupon/selectlist') return json(res, 200, ok(u.searchParams.get('verbose') ? { usable: [], unusable: [] } : []));
      if (p === '/srv/order/submit') { res.writeHead(200, { 'content-type': 'application/json' }); return res.end(JSON.stringify({ success: true, orderId: 1, orderSn: 'SN1', actualPrice: 26.93 })); }
      if (p.startsWith('/srv/order/1/actions/pay')) { res.writeHead(200, { 'content-type': 'application/json' }); return res.end(JSON.stringify({ success: true, orderId: 1, orderSn: 'SN1' })); }
      if (p === '/srv/page/season') return json(res, 200, { errno: 642, errmsg: 'no active season page', data: null });
      if (p === '/srv/track/collect' || p === '/srv/track/consent') return json(res, 200, ok({}));
      return json(res, 200, ok({ list: [], total: 0, pages: 0, limit: 0, page: 1 }));
    }
    // static + SPA fallback
    let file = path.join(STATIC, p);
    if (!path.extname(p) || !fs.existsSync(file)) file = path.join(STATIC, 'index.html');
    if (!fs.existsSync(file)) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { 'content-type': MIME[path.extname(file)] || 'application/octet-stream' });
    fs.createReadStream(file).pipe(res);
  });
}).listen(PORT, () => console.log('stub on', PORT));
