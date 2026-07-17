# Handoff — Wave-7 secret removal (from `platform`)

**From:** `fix/platform` (Wave-7 Task B)
**To:** `order`, `gateway-api` — and one item with **no owner**
**Date:** 2026-07-17

`platform` removed every committed secret it could reach without touching files
the active `order` / `gateway-api` worktrees own. Its scope guard is explicit:
*"creates prod profiles only — does NOT touch default yml, so it cannot conflict
with order/gateway-api."* Five files fall outside that line and are yours.

**This is not optional cleanup.** Task B's acceptance is *"zero secrets in the
repo… gitleaks clean"*, and Task D adds gitleaks to CI (`.gitleaks.toml`,
scanning full history).

**These two files are now the ONLY gitleaks findings in the entire tracked tree.**
Measured, not estimated — gitleaks against the working tree reports exactly:

```
litemall-gateway-api/src/main/resources/config/application.yml:156  [private-key]
litemall-order/src/main/resources/config/application.yml:97         [generic-api-key]
```

Everything else is done. Until you land your two, **CI's gitleaks job fails every
build on every branch** — so this blocks you as much as it blocks `platform`.

Runtime is already safe either way: `docker-compose.prod.yml` sets these via
environment, which outranks the committed values. What remains is deleting the
literals from source.

---

## Pattern to apply

Copy `litemall-promotion-service/src/main/resources/config/application.yml:58`:

```yaml
some-secret: ${SOME_ENV_VAR:}      # env, NO committed fallback
```

The empty default is deliberate — a default is how the DB password reached 6
files. All variable names below already exist in `docker-compose/.env.prod.example`
and are wired in `docker-compose.prod.yml`.

---

## → `order` (3 files)

| File | Line | Secret | Replace with |
|---|---|---|---|
| `litemall-order/src/main/resources/config/application.yml` | ~97 | CJ API key `9520bc…` | `cj-apiKey: ${CJ_API_KEY:}` (+ `cj-email: ${CJ_EMAIL:}`) |
| `litemall-order/src/main/java/…/infrastructure/configuration/GoodsMachineTokenProvider.java` | — | machine-client secret (plaintext literal) | `${GATEWAY_API_CLIENT_SECRET:}` |
| `litemall-order/src/test/resources/sql/litemall_schema.sql` | 7 | DB password (the committed dev value) | `__MYSQL_PASSWORD__` placeholder — see `litemall-db/sql/litemall_schema.sql` for the form used |

**CJ key sprawl, now resolved:** two *different* keys were committed for the same
account — `9520bc…` (order, goods-management, analytic) and `8696a1…`
(litemall-core). Whichever a service happened to load decided which key it
authenticated with. `platform` collapsed every other copy onto a single
`CJ_API_KEY`; order's is the last one. **Pick whichever key is actually live in
the CJ console** and put that one value in `.env` — do not reintroduce a second.

## → `gateway-api` (2 files)

| File | Line | Secret | Replace with |
|---|---|---|---|
| `litemall-gateway-api/src/main/resources/config/application.yml` | ~155-183 | **RSA private key** (full PEM) | `private-key-pem: ${LITEMALL_JWT_PRIVATE_KEY_PEM:}` / `public-key-pem: ${LITEMALL_JWT_PUBLIC_KEY_PEM:}` |
| `litemall-gateway-api/src/main/resources/config/bootstrap.yml` | ~23 | machine-client secret (plaintext literal) | `client-secret: ${GATEWAY_API_CLIENT_SECRET:}` |

`platform` already did the identical edit on `gateway-admin/bootstrap.yml` —
copy that shape.

**A guard is already in place for you.** `platform` added
`litemall-gateway-api/src/main/java/…/config/JwtKeyGuard.java` (new file, no
conflict with your work): under the `prod` profile it throws if the PEMs are
blank. This matters because `RsaKeys.from()` (`litemall-db/auth/RsaKeys.java:49-52`)
**silently generates an ephemeral keypair** when they are — `JwtService:51-56`
only logs a WARN. Without the guard, deleting the committed key would give you a
prod that boots fine and then logs every customer out on each restart, mid-checkout,
with nothing in the logs but a WARN. Dev is unaffected (guard is prod-only).

---

## ⚠ No owner: rotate the burned credentials

Untracking and `${VAR:}` do **not** remove anything from git history. Per the
user's locked decision (2026-07-16) there is **no history rewrite** — so every
value below is still readable by anyone with repo access and must be treated as
**burned and rotated at the provider**, not merely deleted from HEAD:

- **Tencent COS** `AKIDOccMr856…` — read/write on the bucket. Rotate in console.
- **WeChat** app-secret `e0400482…` — rotate in console.
- **Stripe** `sk_test_3Li1…` — test-mode, but it was shipped into the **admin
  browser bundle**. Roll it before live keys exist.
- **CJ** both keys — rotate whichever survives.
- **Both realms' RSA signing keys** + machine-client secrets — the admin key is
  the sharp one: admin RBAC is flat (every admin gets `ROLE_ADMIN`), so a token
  minted from it is fully privileged. Generate NEW pairs; do not reuse.
- **MySQL** — the committed dev password.

## ⚠ Breaking change for local dev (everyone)

`litemall-db/src/main/resources/application-db.yml` no longer carries a password
default. **Before starting any service locally:**

```bash
export MYSQL_PASSWORD=<your local litemall password>
```

Otherwise the Druid pool and Flyway both fail to authenticate. This is the
intended end state (Task B: "no committed fallback"), but it will break a running
dev setup the moment `fix/platform` merges — hence this note.
