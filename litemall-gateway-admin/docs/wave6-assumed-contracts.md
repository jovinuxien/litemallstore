# Wave 6 — admin SPA surfaces built on ASSUMED promotion/order contracts

**From:** gateway-admin worktree, 2026-07-16.
**To:** promotion + order worktrees (and whoever merges first).

At SPA build time neither sibling had committed its Wave-6 handoff
(`litemall-promotion-service/docs/handoff-social-composer.md`,
`litemall-order/docs/handoff-mail-outbox-admin.md` — checked both worktrees
and master). These surfaces were therefore built against the CLAUDE.md Wave-6
block contracts, with tolerant field mapping concentrated in ONE api-slice
file each — contract drift is a one-file fix per vertical.

| SPA surface | Endpoints assumed | Mapping lives in |
|---|---|---|
| Promote composer (goods list/detail) | `GET /srv/private/admin/social/compose-preview?goodsId=` → `{goodsId, goodsName, caption, shareUrl, images[] (or candidateImages), videoUrl?, platforms[]/availability{}: {platform, enabled, available, reason}}`; `POST /srv/private/admin/social/post {goodsId, caption, mediaUrl?, platforms[]}` → per-platform results found in `results[]`, `data[]`, or a bare array; items `{platform, success? or status, error, externalPostId?}` | `app/shared/reducers/private/services/adminSocialApi.ts` |
| Social posts page (`/admin/promotion/social`) | `GET /srv/private/admin/social/list?page=&limit=[&status=&platform=]` → bare array OR `{list,total,pages}` (either bare or under `data`); rows `{id, goodsId, platform meta_fb\|meta_ig\|tiktok, caption, mediaUrl, linkUrl, status draft\|posted\|failed, externalPostId, error, postedBy ('auto' badge), add/updateTime ISO or LocalDateTime array}`; `POST /srv/private/admin/social/{id}/retry` → `{success,message}` | `adminSocialApi.ts` |
| Mail outbox page (`/admin/sys/mail`) | ~~assumed~~ **RECONCILED 2026-07-16** against order's committed `docs/handoff-mail-outbox-admin.md`: legacy envelope list, `POST /{id}/resend` (422 on non-failed rows), lastError tooltip also on pending rows with attempts>0 | `adminMailApi.ts` |
| Campaigns page (`/admin/promotion/campaign`) | Built from committed source (`LitemallCampaignAdminController`, `CampaignManagerDtoResponse`) — normative, not assumed | `adminPromotionApi.ts` |

UI decisions that lean on the block wording (flag if the handoffs disagree):

- **TikTok checkbox** is disabled (tooltip) when the goods has no video OR the
  TikTok adapter is disabled; **Meta checkboxes stay selectable when their
  adapter is disabled** (warning badge) so posting produces the honest
  `failed` ledger rows the acceptance requires.
- **External link-out**: absolute-URL `externalPostId` links through;
  `meta_fb` ids link as `facebook.com/<id>`; IG/TikTok ids render as plain
  text (no public URL derivable) — ship a permalink field if you want a real
  link-out there.
- **Mautic link-out** (campaigns page): `MAUTIC_BASE_URL` webpack build env →
  `<base>/s/segments?search=litemall-campaign-<id>` per
  `MauticProperties.segmentAliasPrefix`; hidden when the env is unset.
- The campaigns admin page did not previously exist; it was created this wave
  to host the link-out (list + activate/evaluate only, no define form).
