# Handoff → gateway-api: freight-quote contract (Wave 4)

`POST /srv/order/freight-quote` (existing route `/srv/order/**` → order service —
no new route needed). `X-User-Id` is OPTIONAL: keep relaying it when the customer is
signed in; anonymous quotes still work (wildcard region matching only).

## Request (all new fields optional — old callers keep working)

```json
{
  "subtotal": 42.50,                  // existing — checked goods subtotal
  "countryCode": "SE",               // existing — checkout country picker
  "addressId": 7,                     // NEW — owner-scoped; resolves the province for
                                      //       region matching. errno 605 when it isn't
                                      //       the caller's (or the call is anonymous).
  "items": [                          // NEW — cart lines; REQUIRED for template pricing
    { "goodsId": 12, "quantity": 3, "price": 9.99 }
  ],                                  //       send price (cart unit price) so free-rule
                                      //       amount thresholds see variant pricing
  "cjItems": [ { "productId": 33, "quantity": 1 } ]   // existing — CJ groups only
}
```

## Response `data` (envelope unchanged: `{errno, errmsg, data}`)

```json
{
  "freightPrice": 11.00,             // existing — what submit WILL charge
  "freeShippingThreshold": 88,       // existing
  "source": "TEMPLATE",              // NEW — TEMPLATE | SYSTEM_FLAT | FREE_MIN
  "breakdown": [                      // NEW — one entry per priced group
    { "templateId": 3, "templateName": "EU parcels", "source": "TEMPLATE",
      "amount": 11.00,
      "note": "region rule (SE): first 1 @ 5.00, per 1 more @ 3.00" },
    { "templateId": null, "templateName": null, "source": "SYSTEM_FLAT",
      "amount": 8, "note": "goods without a freight template — legacy flat value" }
  ],
  "cj": { "logisticName": "CJPacket Ordinary", "logisticAging": "8-12" },  // existing
  "cjNote": "..."                    // existing (NON_NULL — absent unless set)
}
```

- `freightPrice` is the COMBINED figure (`combine-mode: max` by default — it is the
  max of the breakdown amounts, not their sum). Display the breakdown as detail only;
  charge/preview totals must use `freightPrice`.
- Errors: `errno 605` (address not owned / anonymous with addressId) is the only new
  client error; freight problems never produce a 5xx (worst case `SYSTEM_FLAT`).
- Submit is unchanged in shape: the order's `freight_price` is priced by the same
  service, so the quoted `freightPrice` equals the charged freight for the same
  cart/address/country (send the same `items` you are about to submit).
- CJ cart groups: keep sending `cjItems` exactly as today; CJ groups skip templates
  (`source` reflects the flat rule) and the informational `cj` block is unchanged.
