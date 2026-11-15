# Headless acceptance — display currency (stage A)

Real-click run of the BUILT storefront bundle against a stub edge (no Java stack). Spec:
`../../spec-display-currency.md` §5. Last run 2026-11-15: 67/67 assertions, 0 page errors.

```bash
cd litemall-gateway-api/src/main/webapp && npm run -s webapp:prod          # → target/classes/static
cd ../../.. && node docs/headless/display-currency/stub.js "$PWD/target/classes/static" 4312 &
cd .. && NODE_PATH=$PWD/node_modules node litemall-gateway-api/docs/headless/display-currency/drive.js
```

The driver sets each case's `GET /auth/currency` answer through `POST /__scenario` (`'missing'` =
404 from an old edge, `'down'` = 500). The country→currency mapping itself is the edge's job and is
covered by `CurrencyControllerTest`; here the stub plays the edge's answer.

Gotchas met: one browser context PER case (the `lm_cur` cookie and the localStorage rate cache leak
otherwise); compare text with whitespace folded (ICU writes NBSP / narrow NBSP inside amounts); the
order page's side panels read bare arrays — a stub answering them with `{list: []}` crashes the page
and looks like a product bug.
