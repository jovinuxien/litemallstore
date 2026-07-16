# Handoff — Wave-5 affiliate edge (gateway-admin → order worktree)

Status 2026-07-16: gateway-admin Tasks A/B/C are implemented and edge-verified.
Order's engine (`docs/handoff-affiliate-portal.md` in litemall-order) had NOT
been committed when this shipped — the pieces below were built against the
Wave-5 spec in CLAUDE.md and must be reconciled when order lands.

## What the edge does now (context for order)

- `POST /auth/affiliate/{login,refresh,logout}` authenticates `litemall_user`
  rows (BCrypt, `deleted=0`), REQUIRES `is_promoter=1` (else errno 403
  "not an affiliate"), and issues the admin-realm JWT with `typ=affiliate`,
  `sub`/`uid` = **litemall_user.id**, `roles=["ROLE_AFFILIATE"]`. Refresh rides
  `litemall_user_refresh_token` with `login_type='affiliate'` and re-checks
  live-promoter on every rotation.
- `AdminJwtAuthenticationManager` now derives authorities from the JWT `roles`
  claim (whitelist ROLE_ADMIN/ROLE_AFFILIATE); ROLE_ADMIN is no longer granted
  unconditionally. Admin tokens (roles `[ROLE_ADMIN]`) behave identically.
- SecurityConfig: `/srv/private/affiliate/**` requires ROLE_AFFILIATE — ADMIN
  is deliberately NOT allowed there; every ADMIN matcher is untouched.
- Routes (both profiles): `/srv/private/affiliate/**` and
  `/srv/private/admin/extract/**` → `lb://order-service-app`, before the goods
  catch-all. MachineTokenRelayFilter is a GlobalFilter on all lb:// routes, so
  the machine token + `X-User-Id`/`X-User-Type: affiliate`/`X-User-Roles:
  ROLE_AFFILIATE` are relayed exactly as on the admin prefix.

## What order must provide (SPA already calls these)

Envelope `{errno, errmsg, data}`; paged data as
`{list,total,page,limit,pages}`; identity = forwarded `X-User-Id`, self-scoped.

- `GET  /srv/private/affiliate/dashboard` →
  `{available, frozenSum, lifetimeEarned, thisMonth, spreadCount, referredOrders}`
- `GET  /srv/private/affiliate/records?page=&limit=` — V7 ledger rows
  (camelCase: `linkId,linkType,pm,title,price,balance,status,freezeTime,unfreezeTime,addTime`)
- `GET  /srv/private/affiliate/team?page=&limit=` — `{nickname(MASKED),addTime,payCount}`
- `GET  /srv/private/affiliate/links` → `{inviteCode, registerUrl, productUrl}`
  — the SPA replaces a `<goodsId>`/`{goodsId}`/`:goodsId` placeholder in
  `productUrl` (also accepts `productUrlTemplate`).
- `POST /srv/private/affiliate/extract`
  `{extractAmount, realName, extractType, bankCode?, bankAddress?, source:'brokerage'}`
- `GET  /srv/private/affiliate/extract/history?page=&limit=` — litemall_user_extract rows
  (camelCase, incl. `extractPrice,status,failMsg,addTime`)
- `GET  /srv/private/admin/extract/list?status=&page=&limit=`,
  `POST /srv/private/admin/extract/approve {id}`,
  `POST /srv/private/admin/extract/reject {id, reason}` (reason → `fail_msg`).
  Status codes assumed 0 pending / 1 processing / 2 completed / -1 rejected
  (LitemallExtractStatus).
- svcsecurity: order must accept the machine token + X-User-* on
  `/srv/private/affiliate/**` the same way it does on `/srv/private/admin/**`
  (no public-paths entry — the prefix is edge-authenticated).

If any field/param name differs, the SPA touchpoints are
`app/shared/reducers/private/services/{affiliateApi,adminAffiliateApi}.ts` —
one-file fixes.

## Deviations / gaps to reconcile

1. **Edge-local JDBC instead of litemall-db** (`auth/AffiliateUserStore`):
   the shared `LitemallUser` domain/mapper doesn't map
   `is_promoter`/`spread_*` yet — that hand-edit is order's this wave, and this
   worktree must not edit other modules. When order's litemall-db lands,
   `EdgeAdminPromoterController`/auth can migrate to `LitemallUserService`
   (optional; the JDBC reads stay correct regardless).
2. **Promoter admin + per-affiliate ledger live at the edge**
   (`/srv/private/admin/promoter/{list,toggle,ledger}`,
   EdgeAdminPromoterController — AdminEdge pattern). If order (or a user
   service) later owns promoter management, migrate with a route change.
3. **Brokerage config group** (`/srv/private/admin/config/brokerage`) filters
   `litemall_brokerage_*` from `queryAll()`; the GET returns `{}` until
   order's config-seed migration runs, and the POST key-guard will 402 until
   the rows exist (correct behaviour — updateConfig silently no-ops unknown
   keys).
4. **Demoting a promoter** revokes their `login_type='affiliate'` refresh
   tokens (edge-side, scoped SQL) — customer sessions untouched. Their access
   JWT stays valid up to its short TTL; order should not care (ledger writes
   key off spread_uid, not sessions).
