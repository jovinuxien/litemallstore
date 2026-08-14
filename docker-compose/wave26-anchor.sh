#!/usr/bin/env bash
#
# wave26-anchor.sh — run the Wave 26 Phase 2 anchor sequence against PRODUCTION.
#
# Follows the runbook at
#   litemall-goods-management/docs/runbook-wave26-phase2-anchor.md
# and reuses litemall-prod.sh's machine-token recipe. Runs ON THE VPS (docker exec).
#
#   ./wave26-anchor.sh simulate      # READ-ONLY. Resolve anchor ids, simulate margin 2.5
#   ./wave26-anchor.sh status        # READ-ONLY. Current overrides + anchor price shape
#   ./wave26-anchor.sh floor-check 5 # READ-ONLY. What a EUR 5 floor would off-sale RIGHT NOW
#   ./wave26-anchor.sh apply-margin  # MUTATES. PUT margin 2.5 on both anchor L1s
#
#   ./wave26-anchor.sh narrow-preview   # READ-ONLY. Per-L1 split: what stays, what goes
#   ./wave26-anchor.sh narrow-dry       # READ-ONLY. Real walk, real counts, stages nothing
#   ./wave26-anchor.sh narrow-apply     # MUTATES. Queue every non-anchor good for off-sale
#   ./wave26-anchor.sh narrow-execute   # MUTATES. Flip them now (else the 02:00 pass does)
#   ./wave26-anchor.sh narrow-restore 1036012   # MUTATES. Put one L1 back on sale
#
# ORDER MATTERS (the trap this script exists to prevent): apply the margin, let ONE
# nightly cycle land the reprice, verify, and only THEN set LITEMALL_GOODS_PRICE_FLOOR.
# At today's 1.25x prices a EUR 5 floor off-sales ~808 anchor SKUs instead of ~429 —
# it runs cleanly and looks correct while being wrong by roughly double. `floor-check`
# is here so that number is measured against live data before anyone sets the env.
#
# Nothing here deletes anything. The margin override is reversible (DELETE restores the
# global 1.25) and the floor off-sales are an is_on_sale flip.
#
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env.prod"
PROJECT="litemall-prod"
TARGET_MARGIN="2.5"
ANCHOR_NAMES="'Home, Garden & Furniture','Home Improvement'"

RED=$'\e[31m'; GRN=$'\e[32m'; YEL=$'\e[33m'; BLD=$'\e[1m'; RST=$'\e[0m'
step() { printf '\n%s==> %s%s\n' "$BLD" "$*" "$RST"; }
info() { printf '    %s\n' "$*"; }
ok()   { printf '    %s%s%s\n' "$GRN" "$*" "$RST"; }
# warn goes to STDERR on purpose: require_anchor's output is captured by command
# substitution, so a warning printed on stdout would be parsed as a category id.
warn() { printf '    %s%s%s\n' "$YEL" "$*" "$RST" >&2; }
die()  { printf '%sERROR: %s%s\n' "$RED" "$*" "$RST" >&2; exit 1; }

cn() { echo "${PROJECT}-$1-1"; }

[[ -f "$ENV_FILE" ]] || die "no $ENV_FILE — run this on the VPS, from the repo's docker-compose/ dir."

sql() {  # read-only SQL, piped in on stdin
  docker exec -i "$(cn mysql)" sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N 2>/dev/null'
}

# Mint a machine token exactly the way the gateway does (same recipe as litemall-prod.sh).
machine_token() {
  local secret; secret="$(grep '^GATEWAY_API_CLIENT_SECRET=' "$ENV_FILE" | cut -d= -f2-)"
  docker exec "$(cn authserver)" sh -c \
    "curl -s -X POST -u 'gateway-api:$secret' -d 'grant_type=client_credentials' http://localhost:8089/oauth2/token 2>/dev/null" 2>/dev/null \
    | grep -oE '"access_token":"[^"]+"' | cut -d'"' -f4
}

api() {  # api GET|PUT <path> [json-body]
  local method="$1" path="$2" body="${3:-}" tok
  tok="$(machine_token)"
  [[ -n "$tok" ]] || die "could not mint a machine token (check the auth chain: litemall-prod.sh doctor)"
  local args="-s -X $method --max-time 120 -H 'Authorization: Bearer $tok' -H 'X-User-Id: 1' -H 'X-User-Roles: ROLE_ADMIN'"
  [[ -n "$body" ]] && args="$args -H 'Content-Type: application/json' -d '$body'"
  docker exec "$(cn goods-management)" sh -c \
    "curl $args -w '\n%{http_code}' 'http://localhost:8082$path' 2>/dev/null" 2>/dev/null
}

anchor_ids() {
  sql <<SQL
SELECT id FROM litemall.litemall_category WHERE pid=0 AND deleted=0 AND name IN ($ANCHOR_NAMES);
SQL
}

anchor_label() {
  sql <<SQL
SELECT CONCAT(id, '  ', name) FROM litemall.litemall_category
WHERE pid=0 AND deleted=0 AND name IN ($ANCHOR_NAMES);
SQL
}

require_anchor() {
  local ids; ids="$(anchor_ids)"
  [[ -n "$ids" ]] || die "no anchor L1 categories matched $ANCHOR_NAMES on PROD — resolve the real names first (ids are per-database)."
  local n; n="$(echo "$ids" | grep -c .)"
  [[ "$n" == 2 ]] || warn "expected 2 anchor roots, found $n — check the names before applying anything."
  echo "$ids"
}

cmd_simulate() {
  step "Anchor L1 categories on PROD"
  anchor_label | while read -r line; do info "$line"; done
  local ids; ids="$(require_anchor)"

  step "Simulating margin $TARGET_MARGIN (READ-ONLY — no price is changed)"
  for id in $ids; do
    local out code body
    out="$(api GET "/srv/private/admin/insight/categories/$id/simulate?margin=$TARGET_MARGIN")"
    code="${out##*$'\n'}"; body="${out%$'\n'*}"
    if [[ "$code" == 200 ]]; then
      ok "category $id: $body"
    else
      warn "category $id: HTTP $code — $body"
    fi
  done
  cat <<'NOTE'

    READ THIS BEFORE APPLYING:
      * goodsCount must be NON-ZERO and roughly match the on-sale anchor count.
        A near-zero count means costs are missing and the override would reprice
        nothing (that is exactly why this was not run on dev).
      * Expected shape from the live feed: median EUR 9.13 -> 18.26,
        EUR 25-80 band 17.5% -> 24.0%. If live diverges materially, STOP —
        cost data moved and the margin decision needs re-checking.
    Then: ./wave26-anchor.sh apply-margin
NOTE
}

cmd_status() {
  step "Current margin overrides"
  local out code body
  out="$(api GET "/srv/private/admin/insight/margin-overrides")"
  code="${out##*$'\n'}"; body="${out%$'\n'*}"
  [[ "$code" == 200 ]] && info "$body" || warn "HTTP $code — $body"

  step "Anchor price shape (live DB)"
  sql <<SQL
SELECT CONCAT(c.name, ': on_sale=', COUNT(*),
              '  costed=', SUM(g.cost IS NOT NULL AND g.cost>0),
              '  median_ish_retail=', ROUND(AVG(g.retail_price),2))
FROM litemall.litemall_goods g
JOIN litemall.litemall_category c ON c.id = g.category_id
WHERE g.is_on_sale=1 AND g.deleted=0
  AND c.id IN (SELECT id FROM litemall.litemall_category WHERE pid=0 AND name IN ($ANCHOR_NAMES))
GROUP BY c.name;
SQL
  info "(goods hang off leaf categories; this counts only goods sitting directly on the L1 row)"
}

cmd_floor_check() {
  local floor="${1:-5}"
  step "What a EUR $floor floor would off-sale AGAINST TODAY'S LIVE PRICES"
  sql <<SQL
SELECT CONCAT('whole catalogue: ', SUM(retail_price < $floor), ' of ', COUNT(*), ' on-sale goods')
FROM litemall.litemall_goods WHERE is_on_sale=1 AND deleted=0;
SQL
  warn "If the 2.5x margin has NOT yet landed a nightly cycle, this count is roughly"
  warn "DOUBLE what it will be afterwards. Do not set LITEMALL_GOODS_PRICE_FLOOR yet."
  info "Target after the reprice lands: ~429 anchor SKUs at a EUR 5 floor."
}

cmd_apply_margin() {
  local ids; ids="$(require_anchor)"
  step "APPLYING margin $TARGET_MARGIN to the anchor L1s — this changes prices"
  warn "Repricing lands at the NEXT nightly promote, from stored cost."
  warn "Reversible: DELETE /srv/private/admin/insight/categories/{id}/margin restores 1.25."
  read -r -p "    Type APPLY to continue: " confirm
  [[ "$confirm" == "APPLY" ]] || die "aborted (nothing was changed)."

  for id in $ids; do
    local out code body
    out="$(api PUT "/srv/private/admin/insight/categories/$id/margin" "{\"margin\": $TARGET_MARGIN}")"
    code="${out##*$'\n'}"; body="${out%$'\n'*}"
    if [[ "$code" == 200 ]]; then ok "category $id: $body"; else warn "category $id: HTTP $code — $body"; fi
  done

  step "Next"
  cat <<'NOTE'
    1. Wait for ONE nightly cycle (03:00 sync -> 03:30 enrich promote).
    2. ./wave26-anchor.sh status        # confirm prices actually moved
    3. ./wave26-anchor.sh floor-check 5 # now the count is the real one
    4. Only then set LITEMALL_GOODS_PRICE_FLOOR=5.00 and recreate goods-management.
NOTE
}


# ---- Wave 26 deliverable 3: narrowing the storefront to the anchor -------------------------

# The anchor ids, comma-joined, as the narrowing endpoints want them.
anchor_csv() { require_anchor | paste -sd, - ; }

cmd_narrow_preview() {
  step "Per-L1 on-sale split (READ-ONLY — nothing is staged)"
  local ids out code body
  ids="$(anchor_csv)"
  info "anchors: $ids"
  out="$(api GET "/srv/private/admin/insight/narrow/preview?anchorCategoryIds=$ids")"
  code="${out##*$'\n'}"; body="${out%$'\n'*}"
  [[ "$code" == 200 ]] && info "$body" || { warn "HTTP $code — $body"; return 1; }
  cat <<'NOTE'

    totals.keep          = what stays on sale (the anchor storefront)
    totals.wouldOffSale  = what a narrow run would take off sale
    orphanedCategoryGoods= goods whose category does not resolve to any L1. These are
                           narrowed, NOT kept — "cannot place it" must not silently mean
                           "leave it on sale". If this is large, investigate first.
NOTE
}

cmd_narrow_dry() {
  step "DRY RUN — walks and counts exactly like the real thing, stages nothing"
  local ids out code body
  ids="$(anchor_csv | tr ',' ' ')"
  local json; json="{\"anchorCategoryIds\": [$(echo "$ids" | tr ' ' ',')], \"dryRun\": true}"
  out="$(api POST "/srv/private/admin/insight/narrow" "$json")"
  code="${out##*$'\n'}"; body="${out%$'\n'*}"
  [[ "$code" == 200 ]] && ok "$body" || warn "HTTP $code — $body"
}

cmd_narrow_apply() {
  local ids; ids="$(anchor_csv | tr ',' ' ')"
  step "STAGING the narrowing — every on-sale good OUTSIDE the anchor is queued for off-sale"
  warn "This does NOT flip anything yet: it writes approved retire-candidate rows."
  warn "The flip happens at the 02:00 executor, or immediately via: $0 narrow-execute"
  warn "Reversible: $0 narrow-restore <L1 id>   (rows are kept, never deleted)"
  read -r -p "    Type NARROW to continue: " confirm
  [[ "$confirm" == "NARROW" ]] || die "aborted (nothing was staged)."

  local json out code body
  json="{\"anchorCategoryIds\": [$(echo "$ids" | tr ' ' ',')]}"
  out="$(api POST "/srv/private/admin/insight/narrow" "$json")"
  code="${out##*$'\n'}"; body="${out%$'\n'*}"
  [[ "$code" == 200 ]] && ok "$body" || { warn "HTTP $code — $body"; return 1; }
  info "Next: $0 narrow-execute   (or wait for the 02:00 pass)"
}

cmd_narrow_execute() {
  step "Running the retirement executor now (off-sale + per-goods reindex)"
  warn "This is the step customers see. Expect it to take a while — one reindex per goods."
  read -r -p "    Type EXECUTE to continue: " confirm
  [[ "$confirm" == "EXECUTE" ]] || die "aborted (nothing was flipped)."
  local out code body
  out="$(api POST "/srv/private/admin/insight/retire/run")"
  code="${out##*$'\n'}"; body="${out%$'\n'*}"
  [[ "$code" == 200 ]] && ok "$body" || warn "HTTP $code — $body"
}

cmd_narrow_restore() {
  local id="${1:-}"
  [[ -n "$id" ]] || die "usage: $0 narrow-restore <L1 category id>"
  step "RESTORING L1 $id — putting back ONLY goods a narrowing run took off sale"
  info "Goods off-saled by the price floor, hygiene or scored retirement are NOT touched."
  read -r -p "    Type RESTORE to continue: " confirm
  [[ "$confirm" == "RESTORE" ]] || die "aborted (nothing was restored)."
  local out code body
  out="$(api POST "/srv/private/admin/insight/narrow/restore" "{\"categoryIds\": [$id]}")"
  code="${out##*$'\n'}"; body="${out%$'\n'*}"
  [[ "$code" == 200 ]] && ok "$body" || warn "HTTP $code — $body"
}

case "${1:-simulate}" in
  simulate)     cmd_simulate ;;
  status)       cmd_status ;;
  floor-check)  cmd_floor_check "${2:-5}" ;;
  apply-margin) cmd_apply_margin ;;
  narrow-preview) cmd_narrow_preview ;;
  narrow-dry)     cmd_narrow_dry ;;
  narrow-apply)   cmd_narrow_apply ;;
  narrow-execute) cmd_narrow_execute ;;
  narrow-restore) cmd_narrow_restore "${2:-}" ;;
  *) die "usage: $0 {simulate|status|floor-check [EUR]|apply-margin|narrow-preview|narrow-dry|narrow-apply|narrow-execute|narrow-restore <id>}" ;;
esac
