/**
 * Amazon-style presentation model for CJ single-group variant lists.
 *
 * CJ products frequently ship ONE spec group (usually named "Specification")
 * whose every value repeats the full product name plus the real options —
 * e.g. 50 values like "Mens Outdoor Casual Beach Sandals Flip-Flops Black 38".
 * Rendered verbatim that is 50 giant buttons. Amazon shows the same data as a
 * Color/Style row plus a compact Size chip row.
 *
 * `analyzeVariantGroup` derives that display: strip the common word prefix,
 * split each residue into (style..., size) when EVERY residue ends in a
 * size-looking token, and expose the dims plus value mapping back to the
 * ORIGINAL full strings — SKU matching everywhere else keeps using the
 * original values; this is presentation only. Anything that doesn't decompose
 * cleanly falls back to `plain` (render exactly as before), and groups with
 * per-value images stay plain (image swatches carry meaning).
 */

export interface VariantValue {
  value: string;
  picUrl?: string;
}

export interface VariantDim {
  /** Display label: "Size", "Color", "Style", or "Option". */
  name: string;
  values: string[];
}

export interface SplitDisplay {
  kind: 'split';
  dims: VariantDim[];
  /** Original full value for a complete pick set; undefined for absent combos. */
  fullValue: (picks: Record<string, string>) => string | undefined;
  /** Pick set for an original full value; undefined when it isn't one. */
  picksOf: (full: string) => Record<string, string> | undefined;
}

export interface PlainDisplay {
  kind: 'plain';
}

export type VariantDisplay = SplitDisplay | PlainDisplay;

// Last-token size shapes: numeric (EU/US shoe & clothing), letter sizes, one-size.
const SIZE_RE = /^(?:\d{1,3}(?:\.\d)?|(?:XXXS|XXS|XS|S|M|L|XL|XXL|XXXL|XXXXL|[2-9]XL)|One-?Size|Free-?Size)$/i;

const COLOR_WORDS = new Set([
  'black', 'white', 'brown', 'blue', 'red', 'green', 'grey', 'gray', 'beige',
  'khaki', 'navy', 'pink', 'purple', 'yellow', 'orange', 'gold', 'silver',
  'cream', 'ivory', 'tan', 'wine', 'coffee', 'apricot', 'camel', 'burgundy',
  'peach', 'mint', 'teal', 'cyan', 'magenta', 'rose', 'coral', 'lavender',
  'violet', 'turquoise', 'champagne', 'bronze', 'multicolor', 'colorful',
]);

const words = (s: string): string[] => s.trim().split(/\s+/).filter(Boolean);

/** Count of leading words shared by every value (never consuming a value whole). */
const commonPrefixLen = (tokenLists: string[][]): number => {
  const first = tokenLists[0];
  let n = 0;
  while (n < first.length) {
    const w = first[n];
    if (!tokenLists.every(t => t.length > n + 1 && t[n] === w)) break;
    n += 1;
  }
  return n;
};

// Combo key separator — style values contain spaces ("Dark Brown"), so the
// key uses a character that can never appear in a spec value.
const KEY_SEP = '\u0000';

export const analyzeVariantGroup = (values: VariantValue[]): VariantDisplay => {
  const plain: PlainDisplay = { kind: 'plain' };
  if (!values || values.length < 2) return plain;
  if (values.some(v => v.picUrl)) return plain; // image swatches stay as-is

  const tokenLists = values.map(v => words(v.value));
  if (tokenLists.some(t => t.length === 0)) return plain;
  const prefixLen = commonPrefixLen(tokenLists);
  const residues = tokenLists.map(t => t.slice(prefixLen));

  // Split each residue as (style words..., trailing size token).
  const parsed = residues.map(r => {
    const last = r[r.length - 1];
    return last != null && SIZE_RE.test(last) ? { style: r.slice(0, -1).join(' '), size: last } : null;
  });

  if (parsed.every(p => p != null)) {
    const styles: string[] = [];
    const sizes: string[] = [];
    const combos = new Map<string, string>();
    parsed.forEach((p, i) => {
      if (!styles.includes(p!.style)) styles.push(p!.style);
      if (!sizes.includes(p!.size)) sizes.push(p!.size);
      combos.set(p!.style + KEY_SEP + p!.size, values[i].value);
    });
    // A combo colliding onto two originals means the decomposition is lossy.
    if (combos.size !== values.length) return plain;
    // Numeric size runs sort numerically (38..47), letter sizes keep order.
    if (sizes.every(s => /^\d{1,3}(?:\.\d)?$/.test(s))) sizes.sort((a, b) => Number(a) - Number(b));

    const hasStyle = styles.some(s => s !== '');
    if (hasStyle && styles.some(s => s === '')) return plain; // mixed shapes — bail
    // Majority rule: CJ color names drift beyond any fixed list ("Peach",
    // brand shades) — label the dim "Color" when MOST values look like colors.
    const colorish = styles.filter(s => words(s).some(w => COLOR_WORDS.has(w.toLowerCase()))).length;
    const styleName = hasStyle && colorish * 2 > styles.length ? 'Color' : 'Style';

    const dims: VariantDim[] = hasStyle
      ? [
          { name: styleName, values: styles },
          { name: 'Size', values: sizes },
        ]
      : [{ name: 'Size', values: sizes }];

    return {
      kind: 'split',
      dims,
      fullValue: picks => {
        const style = hasStyle ? picks[styleName] : '';
        const size = picks['Size'];
        if ((hasStyle && style == null) || size == null) return undefined;
        return combos.get((style ?? '') + KEY_SEP + size);
      },
      picksOf: full => {
        for (const [key, v] of combos) {
          if (v === full) {
            const sep = key.lastIndexOf(KEY_SEP);
            const style = key.slice(0, sep);
            const size = key.slice(sep + 1);
            return hasStyle ? { [styleName]: style, Size: size } : { Size: size };
          }
        }
        return undefined;
      },
    };
  }

  // No size axis, but a real shared prefix: show the shortened residues as a
  // single dim so 50 repeated product names become readable options.
  if (prefixLen >= 2) {
    const labels = residues.map(r => r.join(' '));
    if (new Set(labels).size !== labels.length) return plain; // labels must stay unique
    const byLabel = new Map(labels.map((l, i) => [l, values[i].value] as [string, string]));
    return {
      kind: 'split',
      dims: [{ name: 'Option', values: labels }],
      fullValue: picks => (picks['Option'] != null ? byLabel.get(picks['Option']) : undefined),
      picksOf: full => {
        for (const [l, v] of byLabel) if (v === full) return { Option: l };
        return undefined;
      },
    };
  }

  return plain;
};

/**
 * Per-tile representative images for a split dim (Amazon color swatches):
 * dimValue -> the first SKU image that (a) belongs to that dim value and
 * (b) DIFFERS from the main product photo. Returns an empty map until the
 * catalog carries real per-variant images (today every SKU shares the main
 * photo — the goods-management variantImage capture backfills this), so the
 * tiles stay text-only exactly as before.
 *
 * `skus` = the SKU list projected to {full: specifications[groupIndex], url}.
 */
export const dimImageMap = (
  skus: Array<{ full?: string; url?: string }>,
  display: SplitDisplay,
  dimName: string,
  mainPicUrl?: string
): Record<string, string> => {
  const map: Record<string, string> = {};
  for (const sku of skus) {
    if (!sku.url || !sku.full || sku.url === mainPicUrl) continue;
    const picks = display.picksOf(sku.full);
    const v = picks?.[dimName];
    if (v != null && map[v] == null) map[v] = sku.url;
  }
  return map;
};
