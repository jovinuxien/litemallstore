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
