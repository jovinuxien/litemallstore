#!/usr/bin/env bash
# order-flow-check.sh — READ-ONLY health check of the money path on the production VPS:
#   storefront -> Stripe payment -> webhook -> admin approval -> CJ placement -> mail.
#
# Run it ON the VPS as root, or from the workstation:
#   ssh root@178.105.153.255 'bash -s' < docker-compose/order-flow-check.sh
#
# It changes nothing: SELECTs inside a READ ONLY transaction, GETs against Stripe and the
# order service, and an SMTP login that quits before MAIL FROM (nothing is sent). Secrets
# are used where they already live (inside the containers / .env.prod) and never printed.
#
# Every finding is tagged  OK  /  WARN  /  FAIL . Exit code = number of FAIL lines.
set -u
ENV_FILE=/opt/litemall/docker-compose/.env.prod
ORDER=litemall-prod-order-1
ADMIN=litemall-prod-gateway-admin-1
MYSQL=litemall-prod-mysql-1
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
FAILS=0
ok()   { echo "  OK    $*"; }
warn() { echo "  WARN  $*"; }
fail() { echo "  FAIL  $*"; FAILS=$((FAILS+1)); }
sql()  { docker exec -i $MYSQL sh -c 'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" litemall 2>/dev/null' <<<"START TRANSACTION READ ONLY; $1; COMMIT;"; }
sget() { docker exec $ORDER sh -c 'curl -sS -m 20 -G -u "$LITEMALL_ORDER_STRIPE_SECRET_KEY:" "$@"' _ "$@"; }

echo "== 1. containers ($(date -u +%FT%TZ), checkout $(git -C /opt/litemall rev-parse --short HEAD 2>/dev/null)) =="
for c in order gateway-api gateway-admin goods-management promotion-service mysql authserver; do
  st=$(docker inspect -f '{{.State.Health.Status}}' litemall-prod-$c-1 2>/dev/null || echo missing)
  case "$c:$st" in
    *:healthy) ok "$c healthy" ;;
    *) fail "$c $st" ;;
  esac
done
# promotion reads KAFKA_BROKERS (not LITEMALL_KAFKA_BROKERS). Without it the service dials
# localhost:9092 forever: unhealthy, group-buy auto-refund dead, and a log flood that grows
# the container's shim by gigabytes (prod, 2026-08-10 .. 2026-10-03).
kb=$(docker exec litemall-prod-promotion-service-1 sh -c 'printf %s "$KAFKA_BROKERS"' 2>/dev/null)
[ "$kb" = kafka:9092 ] && ok "promotion-service has KAFKA_BROKERS=$kb" || fail "promotion-service KAFKA_BROKERS='${kb}' (must be kafka:9092)"
avail=$(awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo)
[ "$avail" -gt 2500 ] && ok "server memory: ${avail} MB available" || warn "server memory: only ${avail} MB available — do not run anything heavy on this host"

echo "== 2. configuration shape (inside the order container) =="
docker exec $ORDER sh -c '
echo "STRIPE=$LITEMALL_ORDER_STRIPE_ENABLED KEYMODE=$(printf %s "$LITEMALL_ORDER_STRIPE_SECRET_KEY" | cut -c1-8) WHSEC=${#LITEMALL_ORDER_STRIPE_WEBHOOK_SECRET} CURRENCY=$LITEMALL_ORDER_STRIPE_CURRENCY"
echo "CJKEY=${#LITEMALL_CJ_API_KEY} CJEMAIL=${#CJ_EMAIL} SANDBOX=$LITEMALL_ORDER_CJ_SANDBOX MODE=$LITEMALL_ORDER_CJ_PLACEMENT_MODE"
echo "MAIL=$LITEMALL_CUSTOMERMAIL_ENABLED MAILUSER=${#LITEMALL_CUSTOMERMAIL_USERNAME} MAILPASS=${#LITEMALL_CUSTOMERMAIL_PASSWORD} NOTIFY=${#LITEMALL_CUSTOMERMAIL_ADMIN_NOTIFY} OPS=${#CJ_OPS_MAIL}"
' > "$T/env" 2>/dev/null
. <(tr ' ' '\n' < "$T/env" | sed 's/^/E_/')
[ "${E_STRIPE:-}" = true ] && [ "${E_KEYMODE:-}" = sk_live_ ] && [ "${E_WHSEC:-0}" -gt 10 ] && ok "Stripe enabled, live key, webhook secret set, currency ${E_CURRENCY}" || fail "Stripe config: enabled=${E_STRIPE:-} keymode=${E_KEYMODE:-} whsec_len=${E_WHSEC:-0}"
[ "${E_CJKEY:-0}" -gt 0 ] && [ "${E_CJEMAIL:-0}" -gt 0 ] && [ "${E_SANDBOX:-}" = false ] && ok "CJ armed (sandbox=false), placement mode ${E_MODE}" || fail "CJ config: key_len=${E_CJKEY:-0} email_len=${E_CJEMAIL:-0} sandbox=${E_SANDBOX:-}"
[ "${E_MAIL:-}" = true ] && [ "${E_MAILUSER:-0}" -gt 0 ] && [ "${E_MAILPASS:-0}" -gt 0 ] && ok "mail enabled with an SMTP login" || fail "mail config: enabled=${E_MAIL:-} login_len=${E_MAILUSER:-0} password_len=${E_MAILPASS:-0}  (a blank login = no SMTP AUTH = Brevo refuses everything)"
[ "${E_NOTIFY:-0}" -gt 0 ] && [ "${E_OPS:-0}" -gt 0 ] && ok "admin paid-order notice + CJ ops alert recipients set" || warn "admin notify / ops recipient not set"

echo "== 3. mail =="
python3 - "$ENV_FILE" <<'PY' > "$T/smtp" 2>&1
import smtplib, ssl, sys
env={}
for line in open(sys.argv[1]):
    if '=' in line and not line.lstrip().startswith('#'):
        k,_,v=line.rstrip('\n').partition('='); env[k]=v
try:
    s=smtplib.SMTP(env['LITEMALL_CUSTOMERMAIL_HOST'], int(env.get('LITEMALL_CUSTOMERMAIL_PORT','587')), timeout=15)
    s.ehlo(); s.starttls(context=ssl.create_default_context()); s.ehlo()
    code,_=s.login(env.get('LITEMALL_CUSTOMERMAIL_USERNAME',''), env.get('LITEMALL_CUSTOMERMAIL_PASSWORD','')); s.quit()
    print('OK', code)
except Exception as e:
    print('FAIL', type(e).__name__, str(e)[:120])
PY
grep -q '^OK 235' "$T/smtp" && ok "SMTP login accepted by the relay (nothing sent)" || fail "SMTP login: $(cat "$T/smtp")"
sql "select status, count(*), max(add_time) from litemall_mail_outbox group by status" > "$T/outbox"
if [ -s "$T/outbox" ]; then
  while IFS=$'\t' read -r st n last; do
    case "$st" in failed) fail "mail outbox: $n row(s) FAILED for good (last $last) — see last_error";; pending) warn "mail outbox: $n pending (last $last)";; *) ok "mail outbox: $n $st (last $last)";; esac
  done < "$T/outbox"
else warn "mail outbox is empty — no customer mail has ever been enqueued"; fi

echo "== 4. orders =="
sql "select order_status, count(*), coalesce(sum(actual_price),0), max(add_time) from litemall_order where deleted=0 group by order_status order by 1" | while IFS=$'\t' read -r st n sum last; do
  case $st in 101) l=unpaid;; 102) l=cancelled;; 103) l=auto-cancelled;; 201) l=PAID;; 202) l="refund requested";; 203) l=refunded;; 301) l=shipped;; 401|402) l=received;; *) l="status $st";; esac
  echo "        $n x $l (total $sum, latest $last)"
done
n=$(sql "select count(*) from litemall_order where deleted=0 and order_status=201 and source='cj' and (cj_order_id is null or cj_order_id='') and pay_time < now() - interval 1 day")
[ "${n:-0}" -eq 0 ] && ok "no paid CJ order waiting more than a day for placement" || fail "$n paid CJ order(s) not placed at CJ for more than a day (see 5.)"
n=$(sql "select count(*) from litemall_order where deleted=0 and order_status=202 and update_time < now() - interval 2 day")
[ "${n:-0}" -eq 0 ] && ok "no refund request older than 2 days" || fail "$n refund request(s) unanswered for more than 2 days"
n=$(sql "select count(*) from litemall_order where deleted=0 and order_status=301 and (ship_sn is null or ship_sn='') and ship_time < now() - interval 3 day")
[ "${n:-0}" -eq 0 ] && ok "no shipped order without a tracking number" || warn "$n shipped order(s) still without a tracking number after 3 days"
n=$(sql "select count(*) from litemall_unpaid_order_task where due_at < now() - interval 2 hour")
[ "${n:-0}" -eq 0 ] && ok "unpaid-order sweep is keeping up" || fail "$n unpaid-order task(s) overdue by more than 2 h (sweep stuck?)"
echo "        last order: $(sql "select coalesce(max(add_time),'never') from litemall_order")   last payment: $(sql "select coalesce(max(pay_time),'never') from litemall_order")"

echo "== 5. CJ: balance and the admin approval queue =="
docker exec $ADMIN sh -c '
TOK=$(curl -sS -m 15 -u "gateway-admin:$SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_AUTHSERVER_CLIENT_SECRET" -d grant_type=client_credentials http://authserver:8089/oauth2/token | sed -E "s/.*\"access_token\":\"([^\"]+)\".*/\1/")
H="-H X-User-Id:1 -H X-User-Roles:ROLE_ADMIN"
curl -sS -m 30 -H "Authorization: Bearer $TOK" $H http://order:8085/srv/private/admin/order/cj/balance; echo
curl -sS -m 30 -H "Authorization: Bearer $TOK" $H "http://order:8085/srv/private/admin/order/cj-placement/pending?page=1&limit=50"; echo
' > "$T/cj" 2>/dev/null
python3 - "$T/cj" <<'PY' | tee "$T/cj.out"
import json,sys
lines=[l for l in open(sys.argv[1]).read().splitlines() if l.strip()]
def j(i):
    try: return json.loads(lines[i])
    except Exception: return None
b=j(0); p=j(1)
if not b or b.get('errno')!=0: print('  FAIL  CJ balance unavailable (CJ token or API problem):', (lines[0][:160] if lines else 'no answer'))
else:
    amt=float((b.get('data') or {}).get('amount') or 0)
    print(('  OK    ' if amt>0 else '  FAIL  ')+f'CJ account balance {amt:.2f} USD'+('' if amt>0 else ' — approved orders are paid from this balance and would sit unpaid at CJ'))
if not p or p.get('errno')!=0: print('  FAIL  pending-approval list unavailable')
else:
    d=p.get('data') or {}; rows=d.get('list') or []
    print(('  OK    ' if not rows else '  WARN  ')+f"{d.get('total',0)} paid order(s) waiting in the admin CJ queue")
    for r in rows:
        tag='PARKED (needs Requeue after fixing the cause)' if r.get('parked') else ('ready to approve' if r.get('cjReady') else 'NOT ready')
        print(f"        order {r.get('orderId')}  paid {r.get('payTime')}  {r.get('actualPrice')}  {r.get('country')}  {tag}")
        why=r.get('parkReason') or r.get('holdReason')
        if why: print('          reason:', str(why)[:170])
PY
FAILS=$((FAILS + $(grep -c '^  FAIL' "$T/cj.out")))

echo "== 6. Stripe (live account, GET only) =="
sget https://api.stripe.com/v1/account > "$T/acct"
sget https://api.stripe.com/v1/webhook_endpoints -d limit=20 > "$T/wh"
sget https://api.stripe.com/v1/payment_intents -d limit=20 > "$T/pi"
sget https://api.stripe.com/v1/events -d limit=50 > "$T/ev"
sql "select id, coalesce(nullif(payment_intent_id,''), replace(pay_id,'CREDIT_CARD:','')), actual_price, order_status from litemall_order where deleted=0 and pay_time is not null and (payment_intent_id is not null or pay_id like 'CREDIT_CARD:%')" > "$T/paid"
python3 - "$T" <<'PY' | tee "$T/stripe.out"
import json,sys,datetime,os
T=sys.argv[1]
def load(n):
    try: return json.load(open(os.path.join(T,n)))
    except Exception: return {'error':{'message':'no/invalid answer'}}
a=load('acct')
if 'error' in a: print('  FAIL  Stripe account:', a['error'].get('message'))
else:
    good=a.get('charges_enabled') and a.get('payouts_enabled') and not ((a.get('requirements') or {}).get('currently_due'))
    print(('  OK    ' if good else '  FAIL  ')+f"account {a.get('country')}: charges_enabled={a.get('charges_enabled')} payouts_enabled={a.get('payouts_enabled')} requirements_due={len((a.get('requirements') or {}).get('currently_due') or [])} default_currency={a.get('default_currency')}")
w=load('wh'); eps=[e for e in w.get('data',[]) if 'trovemo.com' in (e.get('url') or '')]
need={'payment_intent.succeeded','payment_intent.payment_failed','charge.refunded'}
if not eps: print('  FAIL  no webhook endpoint registered for trovemo.com')
for e in eps:
    miss=need-set(e.get('enabled_events') or [])
    good=e.get('status')=='enabled' and (not miss or '*' in (e.get('enabled_events') or []))
    print(('  OK    ' if good else '  FAIL  ')+f"webhook {e.get('url')} status={e.get('status')}"+(f" MISSING events {sorted(miss)}" if miss and not good else ''))
ev=load('ev').get('data',[])
stuck=[e for e in ev if (e.get('pending_webhooks') or 0)>0 and e.get('type') in need]
print(('  OK    ' if not stuck else '  FAIL  ')+f"{len(stuck)} payment event(s) not yet delivered to the webhook (of {len(ev)} events in Stripe's 30-day window)")
pis=load('pi').get('data',[])
known={}
for line in open(os.path.join(T,'paid')):
    oid,pi,amt,st=line.rstrip('\n').split('\t'); known[pi]=(oid,amt,st)
print(f"        last {len(pis)} payment intent(s) on the account:")
for p in pis[:10]:
    t=datetime.datetime.fromtimestamp(p['created'], datetime.timezone.utc).strftime('%Y-%m-%d %H:%M')
    o=known.get(p['id']); oid=(p.get('metadata') or {}).get('orderId')
    print(f"          {t}  {p['status']:<24} {p['amount']/100:>8.2f} {p['currency']}  order {oid or '?'}  {'recorded as paid' if o else ''}")
    if p['status']=='succeeded' and not o: print(f"  FAIL  Stripe took money for order {oid} but the order is not recorded as paid")
mine={p['id'] for p in pis}
for pi,(oid,amt,st) in known.items():
    if pi not in mine: print(f"  WARN  order {oid} (status {st}, {amt}) is marked paid with intent {pi[:14]}… which is not among this live account's recent payments (test-mode or old payment — do not ship it without checking)")
PY
FAILS=$((FAILS + $(grep -c '^  FAIL' "$T/stripe.out")))

echo "== 7. edge =="
for p in "/ 200" "/srv/store/list 200" "/srv/order/list 401" "/auth/site-config 200"; do
  set -- $p; code=$(curl -sS -m 20 -o /dev/null -w '%{http_code}' -A 'Mozilla/5.0 (X11; Linux x86_64) Chrome/126' "https://trovemo.com$1")
  [ "$code" = "$2" ] && ok "GET $1 -> $code" || fail "GET $1 -> $code (expected $2)"
done
code=$(curl -sS -m 20 -o /dev/null -w '%{http_code}' -A 'Mozilla/5.0' -X POST -H 'Content-Type: application/json' -d '{}' https://trovemo.com/srv/order/webhook/stripe/order-paid)
[ "$code" = 400 ] && ok "unsigned webhook POST rejected ($code) — signature gate is on" || fail "unsigned webhook POST -> $code (expected 400)"
n=$(docker logs --since 24h $ORDER 2>&1 | grep -c " ERROR ")
[ "$n" -eq 0 ] && ok "order service: 0 ERROR lines in the last 24 h" || warn "order service: $n ERROR line(s) in the last 24 h — docker logs --since 24h $ORDER | grep ' ERROR '"

echo
echo "RESULT: $FAILS FAIL line(s). $( [ $FAILS -eq 0 ] && echo 'The money path is configured and clear.' || echo 'Fix the FAIL lines above before relying on the flow.')"
exit $FAILS
