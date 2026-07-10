# Handoff — engagement endpoints live on goods-management (2026-07-07)

Producer: `litemall-goods-management` (this worktree). Consumer: `litemall-gateway-api`
customer SPA, which already calls every path below behind an `isMissingEndpoint` (404/501)
guard. **Ship-side is done** — the gateway-api worktree only has to remove the guards.

The customer-engagement gateway route (`/srv/collect/**`, `/srv/footprint/**`,
`/srv/feedback/**`, `/srv/comment/**` → `lb://litemall-goods-management`, machine token +
`X-User-Id` relay) is already landed per
`litemall-gateway-api/docs/handoff-goods-management-engagement.md`.

## Shape reconciliation — NO drift

Cross-checked live responses against the SPA call sites
(`litemall-gateway-api/src/main/webapp/app/shared/api/userApi.ts`) and consuming views. Every
request path, body field, query param (`PageParams` = `page`/`limit`), and response field the
views read matches the handoff spec exactly. Nothing for the gateway-api worktree to adapt.

| SPA call | Endpoint | Live response `data` |
|---|---|---|
| `collectList(type, {page,limit})` | `GET /srv/collect/list?type=&page=&limit=` | `{list:[{id,type,valueId,name,brief,picUrl,retailPrice}], total}` |
| `collectToggle(type, valueId)` | `POST /srv/collect/addordelete {type,valueId}` | `{type,valueId,collected}` — toggle |
| `footprintList({page,limit})` | `GET /srv/footprint/list?page=&limit=` | `{list:[{id,goodsId,addTime,name,brief,picUrl,retailPrice}], total}` |
| `footprintRecord(goodsId)` | `POST /srv/footprint/record {goodsId}` | ack (fire-and-forget; deduped per user+goods+day) |
| `footprintDelete(id)` | `POST /srv/footprint/delete {id}` | ack (402 if not the caller's row) |
| `feedbackSubmit({content,mobile,type})` | `POST /srv/feedback/submit` | ack |
| `commentPost(ICommentPost)` | `POST /srv/comment/post {type,valueId,star,content,picUrls?}` | `{id}` |

`valueId`/`goodsId` accept a numeric goods id **or** `cj_<pid>`; the `cj_<pid>` form resolves
through the CJ→native linkage (`findGoodsIdByCjPid`) to the promoted goods row, so the
engagement tables only ever store the numeric goods id. Verified live for both forms.

## Identity & security

- Owner is ALWAYS the gateway-injected `X-User-Id` — no caller-supplied `userId` param on any
  customer endpoint (cart-IDOR rule). Verified: user 2 sees none of user 1's collect rows and
  cannot delete user 1's footprint row.
- `/srv/collect/**`, `/srv/footprint/**`, `/srv/feedback/**` are **authenticated** — deliberately
  NOT added to `litemall.svcsecurity.public-paths`, so svcsecurity's deny-by-default requires a
  valid machine token + forwarded user.
- `/srv/comment/**` stays public (anonymous review *reads*). `POST /srv/comment/post` therefore
  enforces its own login check: no `X-User-Id` → `501 unlogin`, even though the path is public.
  In prod the edge strips client `X-User-*` and injects the trusted id after auth, so an
  anonymous visitor cannot post and a logged-in customer's review is attributed correctly.

## Comment purchase check — v1 decision

Any authenticated user may post a review; there is **no** purchased-this-goods verification.
`litemall_comment` carries no purchase flag, and order data lives in litemall-order — verifying
here would need a cross-context order facade, which stays out of goods-management by directive
([[project_cj_order_placement_in_order]]). **Follow-up for the `order` worktree:** expose a
"did user U purchase goods G" query so a later revision can set a real `hasPurchased`.

## Admin surface

`/srv/private/admin/{collect,footprint,feedback}/list` (ROLE_ADMIN via svcsecurity) mirror the
existing `user`/`address` admin lists — optional `userId` filter, `ResponseUtil.okList` paging.
Cross-user admin reads, so `userId` is a filter param here (the caller is an admin, not the owner).
