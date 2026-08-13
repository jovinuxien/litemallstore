#!/bin/bash
#
# Wave 26 Phase 1 — CJ EU-warehouse survival probe (READ-ONLY).
#
# Answers the Wave-26 gate: for each CJ L1 category, how much of CJ's supply is
# actually held in EU warehouses (verified stock) versus the whole catalogue?
# A thin EU tail under "Home, Garden & Furniture" invalidates the anchor choice,
# so this must run BEFORE any catalogue change.
#
# WHY A SAMPLE: CJ's /product/getCategory exposes ids only at the THIRD (leaf)
# level — categoryFirstName/categorySecondName carry no id — so /product/list
# can only be filtered by leaf. ~540 leaves exist; this probe samples LEAVES_PER_L1
# evenly-spread leaves per L1 and reports the ratio with its denominators visible.
# It never claims a census.
#
# COST: 2 paced /product/list calls per sampled leaf (pageSize=1 — we read
# data.total, never the rows). Default 8 leaves x 14 L1s x 2 = ~224 calls at
# PACE seconds each (~11 min). CJ's daily API POINTS budget is shared with the
# nightly prod syncs — run this well away from 03:00 UTC and watch the
# pointsInfo line printed at the end.
#
# READ-ONLY: only /authentication/getAccessToken, /product/getCategory and
# /product/list are called. Nothing is written to CJ or to any database.
#
# USAGE
#   CJ_EMAIL=... CJ_API_KEY=... ./cj-eu-warehouse-probe.sh
#   ./cj-eu-warehouse-probe.sh /path/to/.env.prod      # sources creds from a file
#   LEAVES_PER_L1=4 ./cj-eu-warehouse-probe.sh --dry-run
#
# ENV
#   CJ_EMAIL, CJ_API_KEY   required (prod values live in the VPS .env.prod)
#   EU_COUNTRIES           default "DE,FR,ES,CZ,PL,IT" — CJ's EU warehouse set.
#                          DK/SE have no CJ warehouse; they are served from these.
#   LEAVES_PER_L1          default 8
#   PACE                   default 3 (seconds between list calls; CJ allows 1/s)
#   OUT                    default ./cj-eu-survival-<date>.csv
#
set -uo pipefail

API="https://developers.cjdropshipping.com/api2.0/v1"
EU_COUNTRIES="${EU_COUNTRIES:-DE,FR,ES,CZ,PL,IT}"
LEAVES_PER_L1="${LEAVES_PER_L1:-8}"
PACE="${PACE:-3}"
OUT="${OUT:-./cj-eu-survival-$(date +%Y%m%d-%H%M).csv}"
DRY_RUN=0

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    *) [ -f "$arg" ] && { set -a; . "$arg"; set +a; echo "sourced creds from $arg"; } ;;
  esac
done

if [ -z "${CJ_EMAIL:-}" ] || [ -z "${CJ_API_KEY:-}" ]; then
  echo "ERROR: CJ_EMAIL and CJ_API_KEY must be set (or pass an env file as \$1)." >&2
  exit 1
fi
command -v jq >/dev/null || { echo "ERROR: jq is required." >&2; exit 1; }

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

TOTAL_LEAVES=$(echo "$LEAVES" | grep -c . || true)
echo "    $TOTAL_LEAVES leaves across $(echo "$LEAVES" | cut -f1 | sort -u | grep -c .) L1 categories."

# evenly-spread sample of LEAVES_PER_L1 leaves per L1 (deterministic, not random:
# reruns are comparable and the sample is reproducible in the report)
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

echo "==> plan: $SAMPLED leaves sampled (<= $LEAVES_PER_L1 per L1) x 2 calls, ${PACE}s pace"
echo "    EU warehouse set: $EU_COUNTRIES (verifiedWarehouse=1)"
echo "    est. wall time: ~$(( SAMPLED * 2 * PACE / 60 )) min   output: $OUT"
if [ "$DRY_RUN" = "1" ]; then
  echo "--dry-run: no CJ /product/list calls made."
  echo "$SAMPLE" | awk -F'\t' '{print "    " $1 "  <- " $3}' | sort | head -40
  exit 0
fi

# ------------------------------------------------------------- probe
# data.total for a pageSize=1 query is the whole matching count — we never page.
list_total() {
  local url="$API/product/list?pageNum=1&pageSize=1&categoryId=$1"
  [ -n "${2:-}" ] && url="$url&countryCode=$2&verifiedWarehouse=1"
  local body; body=$(get "$url")
  if [ "$(echo "$body" | jq -r '.result // false')" != "true" ]; then
    echo "ERR|$(echo "$body" | jq -r '.message // "unknown"' | tr '|,' '  ')"
    return
  fi
  echo "$(echo "$body" | jq -r '.data.total // 0')|"
  LAST_POINTS=$(echo "$body" | jq -r '.pointsInfo // empty' | head -c 200)
}

echo "l1,leaf_id,leaf_name,total_all,total_eu_verified,note" > "$OUT"
# L1 names contain commas ("Home, Garden & Furniture"), so the CSV is for the
# human and a tab-delimited twin drives the aggregation — no CSV parsing in awk.
TSV=$(mktemp); trap 'rm -f "$TSV"' EXIT
LAST_POINTS=""
i=0
while IFS=$'\t' read -r L1 LEAF_ID LEAF_NAME; do
  [ -z "$LEAF_ID" ] && continue
  i=$((i + 1))
  sleep "$PACE"; ALL=$(list_total "$LEAF_ID")
  sleep "$PACE"; EU=$(list_total "$LEAF_ID" "$EU_COUNTRIES")
  a="${ALL%%|*}"; e="${EU%%|*}"; note=""
  [ "$a" = "ERR" ] && { note="${ALL#*|}"; a=""; }
  [ "$e" = "ERR" ] && { note="${EU#*|}"; e=""; }
  printf '%s\n' "$(jq -rn --arg a "$L1" --arg b "$LEAF_ID" --arg c "$LEAF_NAME" \
      --arg d "$a" --arg e "$e" --arg f "$note" '[$a,$b,$c,$d,$e,$f]|@csv')" >> "$OUT"
  printf '%s\t%s\t%s\t%s\n' "$L1" "$a" "$e" "$note" >> "$TSV"
  printf '  [%3d/%3d] %-28.28s %-24.24s all=%-7s eu=%-6s %s\n' \
      "$i" "$SAMPLED" "$L1" "$LEAF_NAME" "${a:-?}" "${e:-?}" "$note"
done <<< "$SAMPLE"

# ------------------------------------------------------------ report
echo
echo "===================== EU-WAREHOUSE SURVIVAL BY L1 ====================="
echo "EU set: $EU_COUNTRIES | verifiedWarehouse=1 | sample: <= $LEAVES_PER_L1 leaves/L1"
echo
printf "%-30s %7s %10s %10s %8s\n" "L1 CATEGORY" "LEAVES" "CJ TOTAL" "EU STOCK" "SURVIVE"
awk -F'\t' '{
    if ($2 == "" || $3 == "") { err[$1]++; next }
    all[$1] += $2; eu[$1] += $3; leaves[$1]++
  }
  END {
    for (k in all) {
      pct = (all[k] > 0) ? (eu[k] * 100.0 / all[k]) : 0
      # leading sort key, stripped after sorting — highest survival first
      printf "%09.4f\t%-30.30s %7d %10d %10d %7.1f%%%s\n", pct, k, leaves[k], all[k], eu[k], pct,
             (err[k] ? "  (" err[k] " errors)" : "")
    }
  }' "$TSV" | sort -rn | cut -f2-
echo
echo "rows: $OUT"
[ -n "$LAST_POINTS" ] && echo "CJ points after run: $LAST_POINTS"
echo
echo "READ THIS BEFORE DECIDING:"
echo " * These are CJ SUPPLY counts, not our catalogue. Our own 15k rows' EU"
echo "   survival needs the enrichment-seam capture (Wave 26 Phase 1b)."
echo " * Sampled leaves, not a census — denominators are in the LEAVES column."
echo " * A category with high CJ total but ~0% EU survival cannot anchor a"
echo "   DE/FR/DK/SE storefront no matter how many SKUs it shows."
