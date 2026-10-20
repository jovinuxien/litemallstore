# Headless acceptance — checkout delivery sync pass 2

Real-click run of the BUILT storefront bundle against a stub edge (no Java stack). Spec:
`../../spec-checkout-delivery-sync-2.md` §3. Last run 2026-10-20: 69/69 assertions, 0 page errors.

```bash
cd litemall-gateway-api/src/main/webapp && npm run -s webapp:prod          # → target/classes/static
cd ../../.. && node docs/headless/checkout-delivery-sync-2/stub.js "$PWD/target/classes/static" 4310 &
cd .. && NODE_PATH=$PWD/node_modules node litemall-gateway-api/docs/headless/checkout-delivery-sync-2/drive.js
```

`stub.js` serves the bundle with SPA fallback and answers `/srv` + `/auth` with canned
envelopes; the driver sets the scenario (saved addresses, enabled languages) through
`POST /__scenario` and reads every recorded API call from `GET /__log` — that is how the
address write-back and the submit body are asserted, not inferred from the screen.
`drive.js` needs `puppeteer-core` (workspace root `node_modules`) and `/usr/bin/google-chrome`.

Gotchas met: each case runs in its OWN browser context — the `lm_lang` cookie and
storage leak between pages of a shared context and silently change the site language
of later cases; React controlled fields need the native value setter + an `input`/
`change` event; "Failed to load resource" console lines on intended 4xx are not page errors.
