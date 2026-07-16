# Handoff: invite code at registration (Wave 5 → gateway-api)

The shared contract for referral attribution. Attribution happens ONCE, at
registration, and is permanent (`litemall_user.spread_uid`). There is no
cookie/click attribution in v1 (explicitly v2 — do not build).

## Code format (shared contract)

```
inviteCode = "A" + base36(uid), uppercase
```

- uid 7  → `A7`
- uid 42 → `A16`
- uid 1295 → `AZZ`

Decode: strip the leading `A` (accept `a` case-insensitively), parse the rest
as base36. There is NO invite-code DB column — the code is derived from the
user id, both directions, everywhere. Order's reference implementation:
`litemall-order/.../interfaces/util/InviteCodes.java` (encode + decode);
gateway-api keeps its decoder in lock-step with this file.

## Resolve rules at `POST /auth/register` (gateway-api)

Request gains an OPTIONAL `inviteCode` field. Resolution:

1. Absent / empty / whitespace → register normally, `spread_uid = 0`.
2. Malformed (wrong prefix, non-base36 tail, overflow, uid ≤ 0) → same:
   register normally, `spread_uid = 0`, errno 0. **An invite problem never
   fails, delays, or decorates registration with an error.**
3. Decoded uid must resolve to a LIVE PROMOTER: a `litemall_user` row that is
   not deleted AND has `is_promoter = 1`. Unknown uid or non-promoter → rule 2.
4. Valid ⇒ on the new user's row set:
   - `spread_uid = <referrer id>`
   - `spread_time = NOW()`
   - `path = referrer.path + referrer.id + "/"` (e.g. referrer path `/0/`,
     id 7 → new user path `/0/7/`)
   and increment the referrer's `spread_count` by 1.

Self-referral cannot happen at registration (the new uid does not exist yet),
so no special case is needed.

## Canonical share URLs (minted by order's `GET /srv/private/affiliate/links`)

- `http://localhost:9000/register?invite=<code>`
- `http://localhost:9000/product/<goodsId>?invite=<code>`

The customer SPA stashes `?invite=` from ANY landing route (sessionStorage)
and submits it with the register call; clear the stash after a successful
registration.

## litemall-db landmine (verified handled on the order branch)

`LitemallUser` + `LitemallUserMapper.xml` on order's branch already round-trip
`spread_uid / spread_time / is_promoter / pay_count / spread_count / path`
through `BaseResultMap`, `Base_Column_List`, `insertSelective`,
`updateByPrimaryKeySelective` and `updateByExampleSelective`. The full
(non-selective) `insert` / `updateByPrimaryKey` statements deliberately do NOT
carry them (a partial entity would write NULL into NOT NULL columns); use the
selective variants. If both branches hand-edited these files, reconcile at
merge — the content should be equivalent.

## Consumer engine (order side, for context)

At pay time order's `BrokerageService` reads the BUYER's `spread_uid`,
re-checks the referrer is still a live promoter, and writes a FROZEN
commission ledger row. `spread_uid = 0` (or a demoted/deleted referrer) means
no commission — silently.
