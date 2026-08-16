#!/usr/bin/env bash
#
# wave26-drain-descriptions.sh — drain the merchant feed's duplicate descriptions.
#
# WHY THIS EXISTS
#   A feed row whose description merely repeats its title is a Merchant Center quality
#   problem and a thin-content signal to Google. Measured on the live feed 2026-08-16:
#   790 of 2,902 rows (27.2%), including 234 of the 1,180 in the EUR 25-80 band we
#   actually intend to advertise.
#
#   Those 790 are NOT one problem. Classified against prod:
#     704  never enriched   -> CJ has a detail body we have simply never fetched.
#      86  enriched, still dry -> CJ genuinely ships no prose for these.
#   Only the first bucket is drainable by fetching. The second needs composed copy and
#   is deliberately out of scope here — this script fixes what fetching can fix, and
#   `status` keeps reporting the residual so it cannot be quietly forgotten.
#
#   The enrichment queue (selectForEnrichment) already sorts on-sale first, then
#   never-enriched first, so the 704 sit at the head of it. This script does not
#   reorder anything; it just runs the existing batch harder than the nightly does.
#
# THE COST, STATED PLAINLY
#   Enrichment calls the CJ API (1 detail + N inventory per product) and CJ's daily
#   points budget is shared ACCOUNT-WIDE WITH ORDER PLACEMENT. Draining aggressively
#   on a day with paid orders awaiting admin approval can leave an approval unable to
#   place. That is why `drain` requires an explicit typed confirmation, stops dead on
#   the service's own quota signal, and never loops unattended.
#
#   ./wave26-drain-descriptions.sh status          # READ-ONLY. Queue + residual + feed shape
#   ./wave26-drain-descriptions.sh drain 4 100     # MUTATES + SPENDS CJ POINTS. 4 rounds x 100
#
# Runs ON THE VPS, from the repo's docker-compose/ dir. Reuses wave26-anchor.sh's recipe.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env.prod"
PROJECT="litemall-prod"

RED=$'\e[31m'; GRN=$'\e[32m'; YEL=$'\e[33m'; BLD=$'\e[1m'; RST=$'\e[0m'
step() { printf '\n%s==> %s%s\n' "$BLD" "$*" "$RST"; }
info() { printf '    %s\n' "$*"; }
ok()   { printf '    %s%s%s\n' "$GRN" "$*" "$RST"; }
warn() { printf '    %s%s%s\n' "$YEL" "$*" "$RST" >&2; }
die()  { printf '%sERROR: %s%s\n' "$RED" "$*" "$RST" >&2; exit 1; }

cn() { echo "${PROJECT}-$1-1"; }

[[ -f "$ENV_FILE" ]] || die "no $ENV_FILE — run this on the VPS, from the repo's docker-compose/ dir."

sql() { docker exec -i "$(cn mysql)" sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N 2>/dev/null'; }

machine_token() {
  local secret; secret="$(grep '^GATEWAY_API_CLIENT_SECRET=' "$ENV_FILE" | cut -d= -f2-)"
  docker exec "$(cn authserver)" sh -c \
    "curl -s -X POST -u 'gateway-api:$secret' -d 'grant_type=client_credentials' http://localhost:8089/oauth2/token 2>/dev/null" 2>/dev/null \
    | grep -oE '"access_token":"[^"]+"' | cut -d'"' -f4
}

# A batch of 100 paces at roughly 10-30s per product against CJ's rate limit, so a round
# can legitimately run past half an hour. The default 120s timeout would abandon the
# client while the server kept working — which reads as a failure and tempts a re-run of
# work that is already in flight, spending the points twice.
api() {
  local method="$1" path="$2" tok
  tok="$(machine_token)"
  [[ -n "$tok" ]] || die "could not mint a machine token (check the auth chain: litemall-prod.sh doctor)"
  docker exec "$(cn goods-management)" sh -c \
    "curl -s -X $method --max-time ${API_TIMEOUT:-5400} -H 'Authorization: Bearer $tok' \
     -H 'X-User-Id: 1' -H 'X-User-Roles: ROLE_ADMIN' -w '\n%{http_code}' 'http://localhost:8082$path' 2>/dev/null" 2>/dev/null
}

# The drainable queue: on-sale CJ goods we have never asked CJ about.
queue_size() {
  sql <<'SQL'
SELECT COUNT(*) FROM litemall.litemall_goods g
JOIN litemall.litemall_cj_product cp ON cp.pid = g.cj_pid AND cp.deleted = 0
WHERE g.deleted = 0 AND g.is_on_sale = 1 AND g.source = 'cj' AND cp.enriched_time IS NULL;
SQL
}

cmd_status() {
  step "Description drain status (PROD)"
  sql <<'SQL' | while IFS=$'\t' read -r label value; do info "$(printf '%-42s %s' "$label" "$value")"; done
SELECT 'on-sale CJ goods (feed rows)', COUNT(*) FROM litemall.litemall_goods
  WHERE deleted=0 AND is_on_sale=1 AND source='cj'
UNION ALL
SELECT 'no detail body yet', COUNT(*) FROM litemall.litemall_goods g
  JOIN litemall.litemall_cj_product cp ON cp.pid=g.cj_pid AND cp.deleted=0
  WHERE g.deleted=0 AND g.is_on_sale=1 AND g.source='cj' AND COALESCE(CHAR_LENGTH(g.detail),0)=0
UNION ALL
SELECT '  ...drainable (never enriched)', COUNT(*) FROM litemall.litemall_goods g
  JOIN litemall.litemall_cj_product cp ON cp.pid=g.cj_pid AND cp.deleted=0
  WHERE g.deleted=0 AND g.is_on_sale=1 AND g.source='cj' AND cp.enriched_time IS NULL
UNION ALL
-- The residual is NOT "enriched with an empty detail column" — that count is zero and
-- reads as a solved problem. These rows HAVE a detail body; it is image-only markup that
-- strips to nothing, so the description falls back to the title anyway. Measure the text
-- that survives tag-stripping, which is what the feed actually sees.
SELECT '  ...residual (enriched, detail is image-only)', COUNT(*) FROM litemall.litemall_goods g
  JOIN litemall.litemall_cj_product cp ON cp.pid=g.cj_pid AND cp.deleted=0
  WHERE g.deleted=0 AND g.is_on_sale=1 AND g.source='cj'
    AND cp.enriched_time IS NOT NULL
    AND CHAR_LENGTH(TRIM(REGEXP_REPLACE(COALESCE(g.detail,''),'<[^>]*>',' '))) < 30
UNION ALL
SELECT 'confirmed delisted at CJ (>=2 strikes)', COUNT(*) FROM litemall.litemall_cj_product
  WHERE deleted=0 AND delisted_strikes >= 2;
SQL
  info ""
  info "The feed regenerates with the nightly refresh; run the feed check after a cycle."
}

cmd_drain() {
  local rounds="${1:-4}" batch="${2:-100}"
  [[ "$rounds" =~ ^[0-9]+$ && "$batch" =~ ^[0-9]+$ ]] || die "usage: drain <rounds> <batch>"

  cmd_status
  local pending; pending="$(queue_size)"
  step "About to spend CJ API points"
  warn "$rounds rounds x $batch products = up to $((rounds * batch)) detail fetches (+ inventory calls)."
  warn "CJ's daily points budget is SHARED WITH ORDER PLACEMENT. If an admin needs to approve"
  warn "a paid order today, exhausting the budget will make that approval fail."
  info "drainable queue right now: $pending"
  read -r -p "    Type DRAIN to proceed: " confirm
  [[ "$confirm" == "DRAIN" ]] || die "aborted (nothing was spent)."

  local i total_enriched=0 total_failed=0
  for ((i = 1; i <= rounds; i++)); do
    pending="$(queue_size)"
    if [[ "$pending" -eq 0 ]]; then ok "queue drained — stopping after $((i - 1)) round(s)."; break; fi

    step "Round $i/$rounds (batch $batch, $pending still unenriched) — $(date -u '+%H:%M:%SZ')"
    local out code body
    out="$(api POST "/srv/private/admin/search/cj-enrich?batch=$batch")" || true
    code="$(echo "$out" | tail -1)"; body="$(echo "$out" | sed '$d')"
    info "HTTP $code  $body"

    [[ "$code" == "200" ]] || { warn "non-200 — stopping rather than hammering a failing service."; break; }

    local e f
    e="$(echo "$body" | grep -oE '"enriched":[0-9]+' | cut -d: -f2)"; e="${e:-0}"
    f="$(echo "$body" | grep -oE '"failed":[0-9]+'   | cut -d: -f2)"; f="${f:-0}"
    total_enriched=$((total_enriched + e)); total_failed=$((total_failed + f))

    # stoppedEarly is the service's own honest signal that it abandoned the batch —
    # quota exhaustion or a real fault streak. Continuing would fire hundreds more
    # calls at a budget that has already said no.
    if echo "$body" | grep -q '"stoppedEarly"'; then
      warn "service stopped the batch early — NOT starting another round."
      warn "$(echo "$body" | grep -oE '"stoppedEarly":"[^"]*"')"
      break
    fi
    [[ "$e" -eq 0 ]] && { warn "round enriched nothing — stopping."; break; }
  done

  step "Drain finished"
  ok "enriched $total_enriched, failed $total_failed across the rounds that ran"
  info "remaining drainable queue: $(queue_size)"
  info ""
  info "Descriptions reach the FEED at the next catalog refresh (nightly 03:00, or a"
  info "container restart's startup refresh). Verify with the feed check afterwards."
}

case "${1:-status}" in
  status) cmd_status ;;
  drain)  shift; cmd_drain "$@" ;;
  *) die "usage: $0 status | drain <rounds> <batch>" ;;
esac
