#!/bin/bash
#
# Wave 26 Phase 1 — CJ warehouse-location survival probe (READ-ONLY).
#
# Answers the Wave-26 gate: for each CJ L1 category, where is the stock actually
# held? A category whose supply sits in CN cannot serve a DE/FR/DK/SE storefront
# on a next-week delivery promise, no matter how many SKUs it lists.
#
# ---------------------------------------------------------------------------
# MEASURED API FACTS (2026-08-13, live against the prod CJ account — do not
# re-derive; the first version of this script got two of them wrong):
#
#  * countryCode accepts ONE code, max 4 characters. A comma list is rejected
#    outright: "countryCode only within 4 characters". There is therefore NO
#    way to ask "any EU warehouse" in a single call — each country costs a call
#    and the results CANNOT be summed (a product stocked in two countries would
#    be double-counted). Per-country columns are reported side by side instead.
#  * CJ's EU warehouse footprint is Germany. On a 13,056-product Home & Garden
#    leaf: DE 92, GB 34, US 818, CN 12,122; ES/CZ/IT/NL/BE/PL/SE/DK/AT/"EU" all
#    return 0. GB is post-Brexit — a GB warehouse does not serve EU customers
#    without customs.
#  * verifiedWarehouse=1 is far stricter than it looks: it cut that DE 92 to 3.
#    It is OFF by default here; set VERIFIED=1 to add verified-only columns.
#  * data.total on a pageSize=1 query is the full match count — never page.
#  * Category ids exist ONLY at leaf level (categoryFirstName/categorySecondName
#    carry no id), so per-L1 figures aggregate sampled leaves, never one call.
# ---------------------------------------------------------------------------
#
# COST: (1 + N countries) paced calls per sampled leaf. Defaults: 6 leaves x 14
# L1s x 3 calls = 252 calls at PACE seconds (~13 min). The daily API POINTS
# budget is shared with the prod nightly syncs — the meter is printed at the end.
#
# READ-ONLY: only /authentication/getAccessToken, /product/getCategory and
# /product/list are called. Nothing is written to CJ or to any database.
#
# USAGE
#   CJ_EMAIL=... CJ_API_KEY=... ./cj-eu-warehouse-probe.sh
#   ./cj-eu-warehouse-probe.sh /path/to/.env.prod      # sources creds from a file
#   LEAVES_PER_L1=3 ./cj-eu-warehouse-probe.sh --dry-run   # plan only (2 calls)
#
# ENV
#   CJ_EMAIL, CJ_API_KEY   required (prod values live in the VPS .env.prod)
#   COUNTRIES              default "DE,US" — queried ONE AT A TIME, never OR-ed
#   VERIFIED               default 0; 1 adds a verifiedWarehouse=1 column per
#                          country (doubles the call count)
#   LEAVES_PER_L1          default 6
#   PACE                   default 3 (seconds between list calls; CJ allows 1/s)
#   RETRIES / RETRY_PAUSE  default 3 attempts, 8s backoff. CJ rejects ~17% of calls
#                          with "QPS limit is 1 time/1second" even at a 3s pace, and an
#                          empty cell drops the whole leaf from the aggregate — which
#                          BIASES per-L1 shares, not merely thins them.
#   OUT                    default ./cj-warehouse-survival-<date>.csv
#
set -uo pipefail

API="https://developers.cjdropshipping.com/api2.0/v1"
COUNTRIES="${COUNTRIES:-DE,US}"
VERIFIED="${VERIFIED:-0}"
LEAVES_PER_L1="${LEAVES_PER_L1:-6}"
# Wave 26 Phase 2 (sourcing): optional CJ-side price band, applied to EVERY /product/list
# call. ⚠ These bound CJ's COST in USD, not our retail in EUR. Our €25–80 retail band at
# margin 2.5 is €10–32 of cost, and at fx 0.866 that is $11.55–$36.95 — passing the EUR
# numbers straight through would silently source a different band.
MIN_PRICE="${MIN_PRICE:-}"
MAX_PRICE="${MAX_PRICE:-}"
# Restrict the sweep to specific L1s (exact names, PIPE-separated). Empty = all 14.
# ⚠ Pipe, not comma: "Home, Garden & Furniture" CONTAINS a comma, so a comma-separated
# list silently splits it into "Home" and "Garden & Furniture" — neither matches, and the
# sweep quietly probes only the other anchor. Same trap the CSV writer below documents.
ONLY_L1="${ONLY_L1:-}"
PACE="${PACE:-3}"
RETRIES="${RETRIES:-3}"          # attempts per call before a cell is recorded empty
RETRY_PAUSE="${RETRY_PAUSE:-8}"  # seconds to back off after a QPS rejection
OUT="${OUT:-./cj-warehouse-survival-$(date +%Y%m%d-%H%M).csv}"
DRY_RUN=0

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    -*)
      # An unrecognised flag must never fall through to the full paced run —
      # a typo like "--dry run" would otherwise silently spend the daily quota.
      echo "ERROR: unknown option '$arg' (did you mean --dry-run?)" >&2; exit 2 ;;
    *)
      if [ -f "$arg" ]; then
        set -a; . "$arg"; set +a; echo "sourced creds from $arg"
      else
        echo "ERROR: '$arg' is not a readable env file." >&2; exit 2
      fi ;;
  esac
done

if [ -z "${CJ_EMAIL:-}" ] || [ -z "${CJ_API_KEY:-}" ]; then
  echo "ERROR: CJ_EMAIL and CJ_API_KEY must be set (or pass an env file as \$1)." >&2
  exit 1
fi
command -v jq >/dev/null || { echo "ERROR: jq is required." >&2; exit 1; }

IFS=',' read -r -a CC <<< "$COUNTRIES"
for c in "${CC[@]}"; do
  [ "${#c}" -le 4 ] || { echo "ERROR: countryCode '$c' exceeds CJ's 4-char limit." >&2; exit 1; }
done

# ---------------------------------------------------------------- auth
echo "==> authenticating as ${CJ_EMAIL%%@*}@..."
AUTH=$(curl -sS -X POST "$API/authentication/getAccessToken" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg e "$CJ_EMAIL" --arg p "$CJ_API_KEY" '{email:$e,password:$p}')")
TOKEN=$(echo "$AUTH" | jq -r '.data.accessToken // empty')
if [ -z "$TOKEN" ]; then
  echo "ERROR: auth failed: $(echo "$AUTH" | jq -c '{code,message}')" >&2
  exit 1
fi
echo "    token acquired."

get() { curl -sS -H "CJ-Access-Token: $TOKEN" "$1"; }

# ------------------------------------------------------- category tree
echo "==> fetching category tree..."
TREE=$(get "$API/product/getCategory")
if [ "$(echo "$TREE" | jq -r '.result // false')" != "true" ]; then
  echo "ERROR: getCategory failed: $(echo "$TREE" | jq -c '{code,message}')" >&2
  exit 1
fi

# L1 name <TAB> leafId <TAB> leafName, one per line
LEAVES=$(echo "$TREE" | jq -r '
  .data[] | .categoryFirstName as $l1
  | .categoryFirstList[]? | .categorySecondList[]?
  | select(.categoryId != null and .categoryId != "")
  | [$l1, .categoryId, .categoryName] | @tsv')

# Wave 26: optional L1 restriction, applied BEFORE sampling so LEAVES_PER_L1 budgets
# the chosen categories rather than being spent across all 14.
if [ -n "$ONLY_L1" ]; then
  LEAVES=$(echo "$LEAVES" | awk -F'\t' -v want="$ONLY_L1" '
    BEGIN { n = split(want, a, "|"); for (i = 1; i <= n; i++) { gsub(/^ +| +$/, "", a[i]); keep[a[i]] = 1 } }
    ($1 in keep)')
  [ -n "$LEAVES" ] || { echo "ERROR: ONLY_L1='$ONLY_L1' matched no L1 category (exact names, pipe-separated)." >&2; exit 1; }
  want_n=$(echo "$ONLY_L1" | awk -F'|' '{print NF}')
  got_n=$(echo "$LEAVES" | cut -f1 | sort -u | grep -c .)
  [ "$want_n" = "$got_n" ] || echo "    ⚠ ONLY_L1 asked for $want_n L1s but matched $got_n — check exact names."
  echo "    restricted to L1: $ONLY_L1"
fi

TOTAL_LEAVES=$(echo "$LEAVES" | grep -c . || true)
echo "    $TOTAL_LEAVES leaves across $(echo "$LEAVES" | cut -f1 | sort -u | grep -c .) L1 categories."

# evenly-spread sample per L1 (deterministic, not random: reruns are comparable
# and the sample is reproducible in the report)
SAMPLE=$(echo "$LEAVES" | awk -F'\t' -v k="$LEAVES_PER_L1" '
  { rows[$1] = rows[$1] $0 "\n"; n[$1]++ }
  END {
    for (l1 in rows) {
      split(rows[l1], a, "\n"); c = n[l1]
      step = (c > k) ? int(c / k) : 1
      picked = 0
      for (i = 1; i <= c && picked < k; i += step) { print a[i]; picked++ }
    }
  }')
SAMPLED=$(echo "$SAMPLE" | grep -c . || true)

COLS=()
for c in "${CC[@]}"; do
  COLS+=("$c")
  [ "$VERIFIED" = "1" ] && COLS+=("${c}_verified")
done
CALLS_PER_LEAF=$(( 1 + ${#COLS[@]} ))

echo "==> plan: $SAMPLED leaves (<= $LEAVES_PER_L1 per L1) x $CALLS_PER_LEAF calls, ${PACE}s pace"
echo "    columns: all ${COLS[*]}   (each country is a SEPARATE call — CJ cannot OR them)"
echo "    est. wall time: ~$(( SAMPLED * CALLS_PER_LEAF * PACE / 60 )) min   output: $OUT"
if [ "$DRY_RUN" = "1" ]; then
  echo "--dry-run: no CJ /product/list calls made."
  echo "$SAMPLE" | awk -F'\t' '{print "    " $1 "  <- " $3}' | sort | head -40
  exit 0
fi

# ------------------------------------------------------------- probe
# CJ enforces a 1 req/s QPS ceiling that bites even at a 3s pace (observed: 14 of
# 84 leaves rejected on a clean run). An un-retried rejection leaves an empty cell,
# and empty cells drop a whole leaf from the aggregate — which silently BIASES the
# per-L1 shares rather than just thinning them. So: retry with a slow backoff.
list_total() {  # $1 leafId, $2 countryCode (optional), $3 verified flag
  local url="$API/product/list?pageNum=1&pageSize=1&categoryId=$1"
  [ -n "${2:-}" ] && url="$url&countryCode=$2"
  [ "${3:-0}" = "1" ] && url="$url&verifiedWarehouse=1"
  [ -n "$MIN_PRICE" ] && url="$url&minPrice=$MIN_PRICE"
  [ -n "$MAX_PRICE" ] && url="$url&maxPrice=$MAX_PRICE"
  local body attempt=0
  while :; do
    body=$(get "$url")
    if [ "$(echo "$body" | jq -r '.result // false')" = "true" ]; then
      POINTS=$(echo "$body" | jq -rc '.pointsInfo // empty')
      echo "$(echo "$body" | jq -r '.data.total // 0')|"
      return
    fi
    attempt=$((attempt + 1))
    [ "$attempt" -ge "$RETRIES" ] && break
    sleep "$RETRY_PAUSE"
  done
  echo "ERR|$(echo "$body" | jq -r '.message // "unknown"' | tr '|,' '  ')"
}

{ printf 'l1,leaf_id,leaf_name,total_all'; for c in "${COLS[@]}"; do printf ',%s' "$c"; done; printf ',note\n'; } > "$OUT"
# L1 names contain commas ("Home, Garden & Furniture"), so the CSV is for the
# human and a tab-delimited twin drives the aggregation — no CSV parsing in awk.
TSV=$(mktemp); trap 'rm -f "$TSV"' EXIT
POINTS=""
i=0
while IFS=$'\t' read -r L1 LEAF_ID LEAF_NAME; do
  [ -z "$LEAF_ID" ] && continue
  i=$((i + 1))
  note=""
  sleep "$PACE"; r=$(list_total "$LEAF_ID"); all="${r%%|*}"
  [ "$all" = "ERR" ] && { note="${r#*|}"; all=""; }
  vals=(); line="  [%3d/%3d] %-26.26s %-22.22s all=%-7s"
  for c in "${COLS[@]}"; do
    cc="${c%_verified}"; v=0; [ "$c" != "$cc" ] && v=1
    sleep "$PACE"; r=$(list_total "$LEAF_ID" "$cc" "$v"); t="${r%%|*}"
    [ "$t" = "ERR" ] && { note="${r#*|}"; t=""; }
    vals+=("$t")
  done
  # CSV (quoted) + TSV twin (aggregation)
  jq -rn --arg a "$L1" --arg b "$LEAF_ID" --arg c "$LEAF_NAME" --arg d "$all" \
         --arg n "$note" --args '[$a,$b,$c,$d] + $ARGS.positional + [$n] | @csv' "${vals[@]}" >> "$OUT"
  printf '%s\t%s' "$L1" "$all" >> "$TSV"
  for t in "${vals[@]}"; do printf '\t%s' "$t" >> "$TSV"; done
  printf '\n' >> "$TSV"
  printf "  [%3d/%3d] %-26.26s %-22.22s all=%-7s" "$i" "$SAMPLED" "$L1" "$LEAF_NAME" "${all:-?}"
  for j in "${!COLS[@]}"; do printf ' %s=%-6s' "${COLS[$j]}" "${vals[$j]:-?}"; done
  printf ' %s\n' "$note"
done <<< "$SAMPLE"

# ------------------------------------------------------------ report
echo
echo "============== WAREHOUSE LOCATION OF CJ SUPPLY, BY L1 =============="
echo "sample: <= $LEAVES_PER_L1 leaves/L1 | verifiedWarehouse=$VERIFIED"
echo "shares are of CJ TOTAL; countries are separate queries and DO NOT sum"
echo
printf "%-28s %6s %10s" "L1 CATEGORY" "LEAVES" "CJ TOTAL"
for c in "${COLS[@]}"; do printf " %10s %7s" "$c" "share"; done; printf "\n"
awk -F'\t' -v ncols="${#COLS[@]}" '
  { if ($2 == "") { next }
    all[$1] += $2; leaves[$1]++
    for (j = 1; j <= ncols; j++) if ($(2+j) != "") sum[$1, j] += $(2+j)
  }
  END {
    for (k in all) {
      line = sprintf("%-28.28s %6d %10d", k, leaves[k], all[k])
      for (j = 1; j <= ncols; j++) {
        pct = (all[k] > 0) ? (sum[k, j] * 100.0 / all[k]) : 0
        line = line sprintf(" %10d %6.2f%%", sum[k, j], pct)
      }
      # leading sort key (share of the FIRST country column), stripped after sort
      printf "%09.4f\t%s\n", (all[k] > 0 ? sum[k, 1] * 100.0 / all[k] : 0), line
    }
  }' "$TSV" | sort -rn | cut -f2-
echo
echo "rows: $OUT"
[ -n "$POINTS" ] && echo "CJ points: $POINTS"
echo
echo "READ THIS BEFORE DECIDING:"
echo " * These are CJ SUPPLY counts, not our catalogue. Our own rows' survival"
echo "   needs the enrichment-seam capture (Wave 26 Phase 1b)."
echo " * Sampled leaves, not a census — denominators are in the LEAVES column."
echo " * Per-country columns are independent queries and must NOT be added"
echo "   together: a product stocked in two countries appears in both."
