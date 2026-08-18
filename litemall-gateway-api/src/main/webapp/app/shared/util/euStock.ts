/**
 * Wave-26 EU stock signal (Phase 1b).
 *
 * The PDP payload carries an `euStock` key ONLY when the backend has a MEASURED,
 * non-zero EU warehouse reading for that product. Three backend states collapse
 * to two here, deliberately:
 *
 *   never probed  -> no key -> no badge   ("we don't know" is not "it's slow")
 *   probed, zero  -> no key -> no badge
 *   probed, > 0   -> key    -> badge
 *
 * Coverage grows slowly with the CJ enrichment rotation, so for a long while most
 * products will legitimately show nothing. That is correct: the delivery claim is
 * per-SKU and per-measurement, never a storewide promise.
 */

export interface EuStock {
  units: number;
  countries: string[];
}

/** Country code -> the name a customer recognises. Only warehouses CJ actually stocks. */
const COUNTRY_NAMES: Record<string, string> = {
  DE: 'Germany',
};

/**
 * Reads the detail payload's `euStock`, tolerating absence and malformed shapes —
 * an older backend simply omits the key, and a badge must never be invented from
 * a partial response.
 */
export const euStockOf = (data: { euStock?: { units?: number; countries?: string[] } | null } | null | undefined): EuStock | null => {
  const raw = data?.euStock;
  if (!raw) return null;
  const units = typeof raw.units === 'number' && Number.isFinite(raw.units) ? raw.units : 0;
  if (units <= 0) return null;
  const countries = Array.isArray(raw.countries) ? raw.countries.filter(c => typeof c === 'string' && c.length > 0) : [];
  return { units, countries };
};

/**
 * Badge text. Names the country when we know it and can render it; otherwise the
 * neutral "Ships from EU stock".
 *
 * Deliberately NO delivery-day promise. We have measured that stock EXISTS in an
 * EU warehouse, not how long CJ takes to get it to a customer — and CJ picks the
 * fulfilling warehouse at order time. "2-4 days" here would be a number nobody
 * measured.
 */
export const euStockLabel = (stock: EuStock): string => {
  const named = stock.countries.map(c => COUNTRY_NAMES[c.toUpperCase()]).filter(Boolean);
  return named.length === 1 ? `Ships from ${named[0]}` : 'Ships from EU stock';
};

/**
 * The origin as a bare VALUE, for surfaces that already carry their own "Ships from"
 * label (the PDP trust rows, the checkout line badges) and would otherwise read
 * "Ships from → Ships from Germany".
 *
 * Same honesty rule as {@link euStockLabel}: callers pass null for unmeasured
 * products and render nothing at all. There is deliberately no "China" fallback —
 * the payload collapses "never probed" and "probed, no EU stock" into the same
 * absent key, so naming an origin for that case would be a guess, and the row it
 * replaced ("Trovemo") was already the wrong kind of guess.
 */
export const euOriginName = (stock: EuStock): string => {
  const named = stock.countries.map(c => COUNTRY_NAMES[c.toUpperCase()]).filter(Boolean);
  return named.length === 1 ? named[0] : 'an EU warehouse';
};

/**
 * Names an ISO-2 code IF it is an EU warehouse we actually stock from; null otherwise.
 *
 * Returning null for everything else — including a perfectly factual "CN" — is a
 * deliberate display choice, not missing coverage. The surfaces that call this
 * (checkout line badges, the courier chooser) follow the same rule the whole EU
 * signal follows: say something only where there is something positive and measured
 * to say, and render nothing otherwise. That mirrors `upgradeDelta`'s null-means-no-
 * label precedent from Wave 24.1, and it keeps us from stamping "Ships from China"
 * across a checkout as a side effect of a plumbing change nobody asked for.
 */
export const originCountryName = (code: string | null | undefined): string | null => {
  if (!code) return null;
  return COUNTRY_NAMES[code.toUpperCase()] ?? null;
};
