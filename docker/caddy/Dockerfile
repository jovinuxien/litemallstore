# Caddy + the Cloudflare DNS module.
#
# Stock caddy:2-alpine can only do the HTTP-01 ACME challenge, which requires
# Let's Encrypt to reach this origin directly on port 80. The moment the
# trovemo.com DNS records are orange-clouded (proxied through Cloudflare's CDN
# to absorb image/asset traffic), HTTP-01 stops working and cert RENEWAL
# silently fails — a total outage up to 90 days later. The cloudflare module
# enables the DNS-01 challenge (`acme_dns cloudflare` in the Caddyfile), which
# proves domain control via the Cloudflare API instead and works regardless of
# proxying. Token comes from CLOUDFLARE_API_TOKEN in .env.prod (Zone.DNS:Edit).
FROM caddy:2-builder-alpine AS builder
RUN xcaddy build --with github.com/caddy-dns/cloudflare

FROM caddy:2-alpine
COPY --from=builder /usr/bin/caddy /usr/bin/caddy
