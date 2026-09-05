#!/usr/bin/env bash
#
# season-tune.sh — operate the seasonal-candidacy rules on PROD (runs ON THE VPS, from the
# repo's docker-compose/ dir; reuses wave26-anchor.sh's machine-token recipe).
#
#   ./season-tune.sh rules                  # GET all season rules; saves a timestamped backup
#   ./season-tune.sh run                    # POST season-candidates/run; prints the run summary
#   ./season-tune.sh terms <key> <file>     # PUT ONLY the terms of one season from a JSON-array file
#   ./season-tune.sh restore <key> <backup> # PUT the terms recorded in a `rules` backup back
#
# Read-only by default (`rules`; `run` writes only candidate rows). The terms PUT patches the
# `terms` column alone — the mapper's updateByKey is selective, so name/window/weights are untouched.
# Bodies travel as FILES copied into the container: the terms JSON is a string holding escaped
# quotes, and nested ssh/docker/curl quoting has already emptied a token once (looked like a 401).
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env.prod"
PROJECT="litemall-prod"
BASE="/srv/private/admin/insight"
die() { echo "ERROR: $*" >&2; exit 1; }
cn() { echo "${PROJECT}-$1-1"; }
[[ -f "$ENV_FILE" ]] || die "no $ENV_FILE — run this on the VPS, from the repo's docker-compose/ dir."

machine_token() {
  local secret; secret="$(grep '^GATEWAY_API_CLIENT_SECRET=' "$ENV_FILE" | cut -d= -f2-)"
  docker exec "$(cn authserver)" sh -c \
    "curl -s -X POST -u 'gateway-api:$secret' -d 'grant_type=client_credentials' http://localhost:8089/oauth2/token 2>/dev/null" 2>/dev/null \
    | grep -oE '"access_token":"[^"]+"' | cut -d'"' -f4
}

# api <METHOD> <path> [body-file]  -> prints body, then the HTTP code on its own last line
api() {
  local method="$1" path="$2" bodyfile="${3:-}" tok
  tok="$(machine_token)"; [[ -n "$tok" ]] || die "could not mint a machine token"
  local extra=""
  if [[ -n "$bodyfile" ]]; then
    docker cp "$bodyfile" "$(cn goods-management):/tmp/season-body.json"
    extra="-H 'Content-Type: application/json' -d @/tmp/season-body.json"
  fi
  docker exec "$(cn goods-management)" sh -c \
    "curl -s -X $method --max-time ${API_TIMEOUT:-600} -H 'Authorization: Bearer $tok' -H 'X-User-Id: 1' -H 'X-User-Roles: ROLE_ADMIN' $extra -w '\n%{http_code}' 'http://localhost:8082$path' 2>/dev/null"
}

cmd_rules() {
  local out; out="$(api GET "$BASE/season-rules")"
  local code="${out##*$'\n'}" body="${out%$'\n'*}"
  [[ "$code" == 200 ]] || die "GET season-rules -> HTTP $code: $body"
  local f="/root/season-rules-backup-$(date -u +%Y%m%dT%H%M%SZ).json"
  printf '%s\n' "$body" > "$f"
  echo "$body" | python3 -m json.tool 2>/dev/null || echo "$body"
  echo "saved: $f"
}

cmd_run() {
  local out; out="$(api POST "$BASE/season-candidates/run")"
  local code="${out##*$'\n'}" body="${out%$'\n'*}"
  echo "$body" | python3 -m json.tool 2>/dev/null || echo "$body"
  [[ "$code" == 200 ]] || die "run -> HTTP $code"
}

# terms <key> <file-with-JSON-array>  e.g. ["autumn","fall","-summer"]
cmd_terms() {
  local key="$1" file="$2"
  [[ -f "$file" ]] || die "no such file: $file"
  python3 - "$file" <<'PY' > /tmp/season-terms-body.json
import json, sys
arr = json.load(open(sys.argv[1]))
assert isinstance(arr, list) and all(isinstance(t, str) and t.strip() for t in arr), "terms must be a JSON array of non-empty strings"
print(json.dumps({"terms": json.dumps(arr)}))
PY
  echo "PUT $BASE/season-rules/$key with: $(cat /tmp/season-terms-body.json)"
  local out; out="$(api PUT "$BASE/season-rules/$key" /tmp/season-terms-body.json)"
  local code="${out##*$'\n'}" body="${out%$'\n'*}"
  echo "-> HTTP $code $body"
  [[ "$code" == 200 && "$body" == *'"errno":0'* ]] || die "terms PUT was not accepted"
  api GET "$BASE/season-rules" | python3 -c "import sys,json; d=json.loads(sys.stdin.read().rsplit('\n',1)[0]); r=[x for x in d['data']['list'] if x['seasonKey']=='$key'][0]; print('now:', r['terms'])"
}

# restore <key> <backup-file-from-rules>
cmd_restore() {
  local key="$1" backup="$2"
  [[ -f "$backup" ]] || die "no such backup: $backup"
  python3 - "$backup" "$key" <<'PY' > /tmp/season-restore-terms.json
import json, sys
d = json.load(open(sys.argv[1]))
r = [x for x in d['data']['list'] if x['seasonKey'] == sys.argv[2]]
assert r, "season not in backup"
print(r[0]['terms'])
PY
  cmd_terms "$key" /tmp/season-restore-terms.json
}

case "${1:-}" in
  rules) cmd_rules ;;
  run) cmd_run ;;
  terms) [[ $# -eq 3 ]] || die "usage: terms <key> <file>"; cmd_terms "$2" "$3" ;;
  restore) [[ $# -eq 3 ]] || die "usage: restore <key> <backup>"; cmd_restore "$2" "$3" ;;
  *) sed -n 2,12p "$0"; exit 1 ;;
esac
