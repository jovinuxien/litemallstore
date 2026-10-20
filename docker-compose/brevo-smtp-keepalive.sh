#!/bin/sh
# Brevo SMTP key keep-alive for trovemo.com.
#
# Brevo marks an SMTP key inactive after 7 days without traffic. The shop sends
# almost no mail at today's order volume, so this sends ONE plain mail a week
# through the key already configured in the order container (nothing is read
# from .env.prod here; the credentials never leave the container).
#
# Installed on the VPS as /usr/local/bin/brevo-smtp-keepalive.sh, run by
# /etc/cron.d/brevo-smtp-keepalive every Monday 06:00 UTC, log in
# /var/log/brevo-smtp-keepalive.log. Exit 0 only when Brevo answered 250.
set -u
CONTAINER="${CONTAINER:-litemall-prod-order-1}"
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

if ! docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -q true; then
  echo "$STAMP FAIL container $CONTAINER is not running"
  exit 1
fi

OUT="$(docker exec -i "$CONTAINER" sh -c '
set -u
f=/tmp/keepalive.$$.eml
cat > "$f" <<MSG
From: Trovemo <$LITEMALL_CUSTOMERMAIL_FROM>
To: $LITEMALL_CUSTOMERMAIL_ADMIN_NOTIFY
Subject: Trovemo SMTP keep-alive $(date -u +%Y-%m-%d)
Date: $(date -R)
Content-Type: text/plain; charset=UTF-8

Weekly message that keeps the Brevo SMTP key active (Brevo deactivates keys after 7 days without traffic).
Sent automatically from the order container on trovemo.com. No action needed.
MSG
curl -sS --max-time 60 --url "smtp://$LITEMALL_CUSTOMERMAIL_HOST:$LITEMALL_CUSTOMERMAIL_PORT" --ssl-reqd \
  --mail-from "$LITEMALL_CUSTOMERMAIL_FROM" --mail-rcpt "$LITEMALL_CUSTOMERMAIL_ADMIN_NOTIFY" \
  --user "$LITEMALL_CUSTOMERMAIL_USERNAME:$LITEMALL_CUSTOMERMAIL_PASSWORD" \
  -T "$f" -o /dev/null -w "smtp=%{http_code}" 2>&1
rc=$?
rm -f "$f"
exit $rc
' 2>&1)"
RC=$?

if [ "$RC" -eq 0 ] && echo "$OUT" | grep -q 'smtp=250'; then
  echo "$STAMP OK   Brevo accepted the keep-alive mail ($OUT)"
  exit 0
fi
echo "$STAMP FAIL curl exit $RC: $OUT"
exit 1
