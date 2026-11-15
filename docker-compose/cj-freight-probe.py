#!/usr/bin/env python3
"""
Landed-cost plan, step 0 — CJ freight probe (READ-ONLY).

Question: what does CJ charge to ship one unit of a product to SE / DE / DK / FR, compared
with what the checkout charges (flat fee, free from the free-shipping line)?
Plan: litemall-order/docs/plan-landed-cost-2026-11-15.md

For every product in the sample it makes:
  1 x GET  /product/query?pid=            (variant id, weights, CJ cost)
  N x POST /logistic/freightCalculate     (one per destination country, quantity 1)
Nothing is written to CJ or to any database. No order is created.

The "default" courier mirrors CjDropshipOrderFacadeImpl.chooseLogistics(options, null):
the configured name (CJPacket Ordinary) when CJ offers it, else the cheapest option.

COST: 223 products x 5 calls = 1,115 calls, about 25 minutes at the default pace. The daily
CJ API points budget is shared with the nightly catalog sync (03:00 UTC) — do not run it then.

USAGE
  CJ_EMAIL=... CJ_API_KEY=... python3 docker-compose/cj-freight-probe.py
  python3 docker-compose/cj-freight-probe.py /path/to/env-file     # KEY=value lines
  python3 docker-compose/cj-freight-probe.py --dry-run             # plan only, 0 CJ calls
  python3 docker-compose/cj-freight-probe.py --limit 3             # 3 products (smoke run)
  Accepted credential names: CJ_CATALOG_EMAIL/CJ_CATALOG_API_KEY (preferred — the catalog
  key pair, so the order service's own token is left alone) or CJ_EMAIL/CJ_API_KEY.

A rerun RESUMES: products already in the output CSV are skipped. The access token is cached
in ~/.cache/litemall-cj-probe-token (mode 600) because CJ allows one token request per
5 minutes.

ENV
  COUNTRIES   default SE,DE,DK,FR
  FROM        default CN (what the order service sends today)
  PACE        default 1.2 seconds between calls (CJ allows 1/s)
  SAMPLE      default litemall-order/docs/data-freight-probe-sample-2026-11-15.csv
  OUT         default litemall-order/docs/data-freight-probe-2026-11-15.csv
  FX          default 0.866 (USD -> EUR, the store's rate)
  FLAT / FREE_MIN  default 6.93 / 76.21 (EUR, the live flat freight rule)
  DEFAULT_LOGISTIC default "CJPacket Ordinary"
"""
import csv
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.request

API = "https://developers.cjdropshipping.com/api2.0/v1"
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COUNTRIES = [c.strip() for c in os.environ.get("COUNTRIES", "SE,DE,DK,FR").split(",") if c.strip()]
FROM = os.environ.get("FROM", "CN")
PACE = float(os.environ.get("PACE", "1.2"))
SAMPLE = os.environ.get("SAMPLE", os.path.join(REPO, "litemall-order/docs/data-freight-probe-sample-2026-11-15.csv"))
OUT = os.environ.get("OUT", os.path.join(REPO, "litemall-order/docs/data-freight-probe-2026-11-15.csv"))
RAW = os.path.splitext(OUT)[0] + ".raw.jsonl"
FX = float(os.environ.get("FX", "0.866"))
FLAT = float(os.environ.get("FLAT", "6.93"))
FREE_MIN = float(os.environ.get("FREE_MIN", "76.21"))
DEFAULT_LOGISTIC = os.environ.get("DEFAULT_LOGISTIC", "CJPacket Ordinary")
TOKEN_CACHE = os.path.expanduser("~/.cache/litemall-cj-probe-token")
RETRIES = 4

FIELDS = ["goods_id", "pid", "vid", "retail_eur", "band", "category", "eu_stock", "variants",
          "cj_cost_usd", "variant_weight_g", "product_weight", "packing_weight", "country",
          "options", "default_name", "default_usd", "default_aging", "cheapest_name",
          "cheapest_usd", "taxes_fee_usd", "error"]

_last_call = [0.0]


def pace():
    wait = PACE - (time.time() - _last_call[0])
    if wait > 0:
        time.sleep(wait)
    _last_call[0] = time.time()


def http(method, path, token=None, body=None):
    """One CJ call. Returns the parsed envelope, or {'result': False, 'message': ...}."""
    headers = {"Content-Type": "application/json", "User-Agent": "curl/8.5.0"}
    if token:
        headers["CJ-Access-Token"] = token
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=40) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"result": False, "code": e.code, "message": "HTTP %s" % e.code}
    except Exception as e:  # timeout, DNS, TLS
        return {"result": False, "code": -1, "message": str(e)}


def call(method, path, token, body=None):
    """Paced call with retry on QPS rejections and transport errors."""
    last = None
    for attempt in range(RETRIES):
        pace()
        last = http(method, path, token, body)
        if last.get("result") is True or last.get("code") == 200:
            return last
        msg = str(last.get("message") or "")
        quota = last.get("code") == 16900500 or "api points" in msg.lower()
        if quota:
            sys.exit("STOPPED: CJ daily API points are exhausted (%s). Rerun tomorrow — the "
                     "run resumes where it stopped." % msg)
        retryable = "qps" in msg.lower() or "too many" in msg.lower() or last.get("code") in (-1, 429, 502, 503)
        if not retryable:
            return last
        time.sleep(4 * (attempt + 1))
    return last


def load_env_file(path):
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        os.environ.setdefault(k.strip(), v.strip().strip('"').strip("'"))


def get_token():
    if os.environ.get("CJ_ACCESS_TOKEN"):
        return os.environ["CJ_ACCESS_TOKEN"]
    try:
        if time.time() - os.path.getmtime(TOKEN_CACHE) < 6 * 3600:
            tok = open(TOKEN_CACHE).read().strip()
            if tok:
                print("    using the cached access token (%s)" % TOKEN_CACHE)
                return tok
    except OSError:
        pass
    email = os.environ.get("CJ_CATALOG_EMAIL") or os.environ.get("CJ_EMAIL")
    key = os.environ.get("CJ_CATALOG_API_KEY") or os.environ.get("CJ_API_KEY")
    if not email or not key:
        sys.exit("ERROR: set CJ_CATALOG_EMAIL + CJ_CATALOG_API_KEY (or CJ_EMAIL + CJ_API_KEY), "
                 "or pass an env file as the first argument.")
    print("==> authenticating as %s@..." % email.split("@")[0])
    r = http("POST", "/authentication/getAccessToken", body={"email": email, "password": key})
    tok = (r.get("data") or {}).get("accessToken") if isinstance(r.get("data"), dict) else None
    if not tok:
        sys.exit("ERROR: auth failed: code %s, %s" % (r.get("code"), r.get("message")))
    os.makedirs(os.path.dirname(TOKEN_CACHE), exist_ok=True)
    fd = os.open(TOKEN_CACHE, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as f:
        f.write(tok)
    return tok


def num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def pick_default(options):
    named = [o for o in options if (o.get("logisticName") or "").strip()]
    if not named:
        return None
    for o in named:
        if o["logisticName"].strip().lower() == DEFAULT_LOGISTIC.lower():
            return o
    return min(named, key=lambda o: num(o.get("logisticPrice")) if num(o.get("logisticPrice")) is not None else 1e18)


def pick_cheapest(options):
    priced = [o for o in options if num(o.get("logisticPrice")) is not None]
    return min(priced, key=lambda o: num(o["logisticPrice"])) if priced else None


def probe_product(token, s, writer, raw):
    base = {k: s.get(k, "") for k in ("goods_id", "pid", "retail_eur", "band", "category", "eu_stock")}
    q = call("GET", "/product/query?pid=" + s["pid"], token)
    raw.write(json.dumps({"pid": s["pid"], "call": "query", "code": q.get("code"),
                          "message": q.get("message")}) + "\n")
    d = q.get("data") if isinstance(q.get("data"), dict) else None
    variants = (d or {}).get("variants") or []
    if not variants:
        writer.writerow(dict(base, error="no CJ detail: %s" % (q.get("message") or "no variants")))
        return 1
    v = variants[0]
    base.update(vid=v.get("vid", ""), variants=len(variants),
                cj_cost_usd=v.get("variantSellPrice", ""), variant_weight_g=v.get("variantWeight", ""),
                product_weight=d.get("productWeight", ""), packing_weight=d.get("packingWeight", ""))
    for cc in COUNTRIES:
        body = {"startCountryCode": FROM, "endCountryCode": cc,
                "products": [{"vid": v.get("vid"), "quantity": 1}]}
        r = call("POST", "/logistic/freightCalculate", token, body)
        raw.write(json.dumps({"pid": s["pid"], "call": "freight", "country": cc, "response": r},
                             ensure_ascii=False) + "\n")
        options = r.get("data") if isinstance(r.get("data"), list) else []
        row = dict(base, country=cc, options=len(options))
        if not options:
            row["error"] = "no options: %s" % (r.get("message") or "")
        else:
            dflt, cheap = pick_default(options), pick_cheapest(options)
            if dflt:
                row.update(default_name=dflt.get("logisticName", ""), default_usd=dflt.get("logisticPrice", ""),
                           default_aging=dflt.get("logisticAging", ""), taxes_fee_usd=dflt.get("taxesFee", ""))
            if cheap:
                row.update(cheapest_name=cheap.get("logisticName", ""), cheapest_usd=cheap.get("logisticPrice", ""))
        writer.writerow(row)
    return 1 + len(COUNTRIES)


def summarise():
    rows = [r for r in csv.DictReader(open(OUT, encoding="utf-8"))]
    priced = [r for r in rows if num(r.get("default_usd")) is not None]
    products = {r["pid"] for r in rows}
    no_detail = {r["pid"] for r in rows if (r.get("error") or "").startswith("no CJ detail")}
    no_lane = [r for r in rows if (r.get("error") or "").startswith("no options")]
    print("\n==== CJ shipping vs what the checkout charges ====")
    print("products %d   no CJ detail %d   country rows priced %d   no courier offered %d"
          % (len(products), len(no_detail), len(priced), len(no_lane)))
    print("flat fee EUR %.2f, free from EUR %.2f, fx %.3f, from %s, default courier '%s'\n"
          % (FLAT, FREE_MIN, FX, FROM, DEFAULT_LOGISTIC))

    def table(title, keyfn, order=None):
        groups = {}
        for r in priced:
            groups.setdefault(keyfn(r), []).append(r)
        keys = order or sorted(groups)
        print(title)
        print("  %-14s %5s %9s %9s %9s %12s %14s" % ("", "rows", "median", "p90", "max", "above charge", "avg shortfall"))
        for k in keys:
            g = groups.get(k)
            if not g:
                continue
            eur = sorted(num(r["default_usd"]) * FX for r in g)
            short = []
            for r in g:
                charged = 0.0 if num(r["retail_eur"]) >= FREE_MIN else FLAT
                gap = num(r["default_usd"]) * FX - charged
                if gap > 0.005:
                    short.append(gap)
            p90 = eur[min(len(eur) - 1, int(len(eur) * 0.9))]
            print("  %-14s %5d %9.2f %9.2f %9.2f %7d (%2.0f%%) %14s"
                  % (k, len(g), statistics.median(eur), p90, eur[-1], len(short), 100.0 * len(short) / len(g),
                     ("%.2f" % (sum(short) / len(short))) if short else "-"))
        print()

    bands = ["5-10", "10-15", "15-25", "25-50", "50-76.21", "76.21-150", "150+"]
    table("By retail price band (EUR; all destinations; CJ default courier in EUR):", lambda r: r["band"], bands)
    table("By destination:", lambda r: r["country"])

    def wbucket(r):
        w = num(r.get("variant_weight_g"))
        if w is None:
            return "unknown"
        for lim, name in ((250, "<=250 g"), (1000, "250 g-1 kg"), (3000, "1-3 kg"), (10000, "3-10 kg")):
            if w <= lim:
                return name
        return ">10 kg"
    table("By variant weight:", wbucket, ["<=250 g", "250 g-1 kg", "1-3 kg", "3-10 kg", ">10 kg", "unknown"])
    table("By origin stock (1 = measured stock in Germany):", lambda r: "eu_stock=" + (r["eu_stock"] or "?"))
    taxed = [r for r in priced if num(r.get("taxes_fee_usd"))]
    print("rows where CJ's quote carries a non-zero taxesFee: %d of %d" % (len(taxed), len(priced)))
    print("\nCSV: %s\nraw CJ responses: %s" % (OUT, RAW))


def main():
    dry, limit = False, None
    args = sys.argv[1:]
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--dry-run":
            dry = True
        elif a == "--summary":
            summarise()
            return
        elif a == "--limit":
            i += 1
            limit = int(args[i])
        elif a.startswith("-"):
            sys.exit("ERROR: unknown option '%s'" % a)
        elif os.path.isfile(a):
            load_env_file(a)
            print("sourced credentials from %s" % a)
        else:
            sys.exit("ERROR: '%s' is not a readable env file" % a)
        i += 1

    sample = list(csv.DictReader(open(SAMPLE, encoding="utf-8")))
    done = set()
    if os.path.exists(OUT):
        done = {r["pid"] for r in csv.DictReader(open(OUT, encoding="utf-8"))}
    todo = [s for s in sample if s["pid"] not in done]
    if limit is not None:
        todo = todo[:limit]
    calls = len(todo) * (1 + len(COUNTRIES))
    print("==> plan: %d products (%d in the sample, %d already done) x %d calls = %d calls, ~%d min"
          % (len(todo), len(sample), len(done), 1 + len(COUNTRIES), calls, calls * max(PACE, 1.4) / 60 + 1))
    print("    from %s to %s; output %s" % (FROM, ",".join(COUNTRIES), OUT))
    if dry:
        print("--dry-run: no CJ calls made.")
        return
    if not todo:
        summarise()
        return

    token = get_token()
    new = not os.path.exists(OUT)
    made = 0
    with open(OUT, "a", newline="", encoding="utf-8") as f, open(RAW, "a", encoding="utf-8") as raw:
        writer = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        if new:
            writer.writeheader()
        for n, s in enumerate(todo, 1):
            made += probe_product(token, s, writer, raw)
            f.flush()
            raw.flush()
            if n % 10 == 0 or n == len(todo):
                print("    %d / %d products (%d calls)" % (n, len(todo), made))
    summarise()


if __name__ == "__main__":
    main()
