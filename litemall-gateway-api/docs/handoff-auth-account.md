# Handoff — customer account self-service (`/auth/*`, Wave 4 Task A)

Owner: gateway-api edge (`gatewayapi/auth`). Storage: `litemall_user` (+ new
`email` column) and `litemall_user_reset_token` — migration
`V37__user_email_reset_token.sql` in litemall-db.

> **V37 apply status (2026-07-13):** the shared dev DB received the V37 DDL
> **manually** (gateway-api runs Flyway-disabled; registering V37 in
> `flyway_schema_history` before the sibling worktrees' V34–V36 would have
> blocked them under `out-of-order: false`). The migration file is written
> **idempotently** (conditional ALTER + `CREATE TABLE IF NOT EXISTS`), so the
> first Flyway-enabled service that boots with a post-merge litemall-db applies
> V37 cleanly over the already-present schema. Do NOT hand-insert a history row.

All endpoints are served by the gateway itself (no downstream route). Envelope
is the litemall `{errno, errmsg, data}`. Authenticated endpoints take the
customer `Authorization: Bearer <token>`; identity comes from the
gateway-internal `X-User-Id` injection (client-supplied values are stripped by
`IdentityForwardingFilter` — forgery-proof).

## Errno table

| errno | meaning | where |
|-------|---------|-------|
| 0 | success | everywhere |
| 401 | bad credentials / bad refresh token | `/auth/login`, `/auth/refresh` |
| 402 | bad argument / policy violation (password 8–72 bytes, ≠ username; invalid email; bad birthday) | register, reset flows, profile |
| 501 | not logged in, or the account behind the token is gone/deleted (no NPE) | `/auth/me`, `/auth/profile`, `/auth/reset` |
| 700 | wrong old password | `/auth/reset` |
| 701 | email reset flow disabled (`litemall.auth.reset-mail.enabled:false`, the default) — SPA hides the "forgot" tab | `/auth/reset/request`, `/auth/reset/confirm` |
| 703 | invalid / expired / already-used reset token | `/auth/reset/confirm` |
| 704 | username already registered (pre-check + `DuplicateKeyException` mapping — race closed by the existing `user_name` unique index) | `/auth/register` |
| 705 | mobile already registered (`''` treated as ABSENT — no dedupe on empty) | `/auth/register`, `/auth/profile` |

## Endpoints

### POST /auth/register
Body `{username, password, nickname?, email?, mobile?}`.
Success: auto-login — **exactly** login's `{token, refreshToken, userInfo}`.
`userInfo` = `{username, nickName, avatarUrl, email, mobile, gender, birthday}`;
`nickName` is the real nickname (falls back to username) on BOTH register and
login. Password policy: 8–72 bytes (BCrypt limit), must differ from username.
Email is validated + lower-cased; invalid non-blank email → 402.

### POST /auth/login  ·  POST /auth/refresh  ·  POST /auth/logout
Unchanged contracts; login's `userInfo` now carries the extended field set
above (additive).

### POST /auth/reset  (authenticated password change)
Body `{oldPassword, newPassword}`. Wrong old → 700. Success revokes **all**
refresh tokens for the user (`revokeAllForUser`) — other sessions die at their
next rotation; outstanding ACCESS tokens stay valid until their ≤2 h TTL
(documented tradeoff, no server-side access-token denylist).

### POST /auth/reset/request  (forgot flow, disabled by default)
Body `{email}`. Disabled → 701. Enabled → **always errno 0** whether or not the
email exists (anti-enumeration); when it maps to exactly one live account, a
single-use token (SHA-256 hash stored, 30-min expiry) is issued and handed to
the `ResetMailSender` port **fire-and-forget** (send failures are logged, never
surfaced). Default binding is a dev no-op sender that logs the raw token —
production must contribute a real bean (`@ConditionalOnMissingBean` seam).
Blank/invalid email → 402 (format-only check; leaks nothing).

### POST /auth/reset/confirm
Body `{token, newPassword}`. Disabled → 701; unknown/expired/used → 703
(consume is a guarded UPDATE — concurrent replay loses the race). Success sets
the password, invalidates all outstanding reset tokens for the user, and
revokes all refresh tokens.

### GET /auth/me
Returns the `userInfo` map (see register). Replaces the dead
`/srv/user/index` stub. Deleted-user token → 501, not an NPE.

### POST /auth/profile
Partial update `{nickname?, email?, mobile?, avatar?, gender?, birthday?}`
(accepts legacy `nickName` key too). Only supplied, non-blank fields change;
clearing a field is not supported (updateByPrimaryKeySelective skips nulls).
Mobile dedupe → 705. Returns the refreshed `userInfo`.

## SPA probe convention

The reset page probes `POST /auth/reset/request` with `{email: ""}` on mount:
`701` = flow disabled → forgot tab hidden; `402` = flow enabled. No account
information leaks either way.

## Not provisioned at register (verified lazy)

- **Wallet:** `litemall_user.now_money` defaults to `0.00` in schema — no row
  or ledger provisioning needed; `/srv/wallet/balance` reads 0 for a fresh user.
- **Loyalty:** the loyalty port materialises accounts lazily on first touch.
- **Register-gift coupon:** deferred — promotion's recorded follow-up
  (`assignForRegister` parity) will hook a user-registered event or an internal
  grant endpoint once promotion picks it up.

## Follow-ups

- **Rate limiting:** `/auth/register`, `/auth/login`, `/auth/reset/request`
  have no throttle at the edge yet — recorded follow-up (bucket per IP).
- **Real ResetMailSender:** SMTP/provider bean + template when reset-mail is
  productionised; flip `litemall.auth.reset-mail.enabled` to true.
- **JWT keys:** dev uses an ephemeral RSA pair (blank PEM config) — tokens die
  on gateway restart. Configure `litemall.jwt.{private,public}-key-pem` for
  stable sessions.
