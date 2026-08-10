/**
 * Wave-25 honest attribution (supplier "Store" vs consumer "Brand").
 *
 * A brand row renders on the storefront ONLY when it is display-enabled:
 * provider-captured supplier rows (source 'cj-supplier', kind 1) land with
 * display_enabled=0 and carry raw legal-entity names that must never render
 * until an admin renames + enables them. Rows without the V60 fields are
 * pre-V60 manual seeds (the same rows V60 backfills to enabled) — supplier
 * rows cannot exist without V60, so treating an ABSENT displayEnabled as
 * enabled can never leak a legal-entity name. An explicit 0/false always
 * hides.
 */

/** kind=1 renders as "Store" (supplier); anything else as consumer "Brand". */
export type AttributionLabel = 'store' | 'brand';

export interface AttributionBrand {
  id?: number;
  name?: string;
  kind?: number | boolean;
  displayEnabled?: number | boolean;
}

export interface Attribution {
  label: AttributionLabel;
  name: string;
}

/**
 * The goods' brand link from the detail payload: `goods.manufacturerId`
 * serializes as `{id}` (value object) but is read tolerantly. 0 = none.
 */
export const brandIdOf = (goods: { manufacturerId?: { id?: number } | number | null } | null | undefined): number => {
  const raw = goods?.manufacturerId;
  const id = typeof raw === 'object' && raw != null ? raw.id : raw;
  return typeof id === 'number' && Number.isFinite(id) && id > 0 ? id : 0;
};

/** Render decision for a brand row; null = render nothing (never fake attribution). */
export const attributionOf = (brand: AttributionBrand | null | undefined): Attribution | null => {
  if (!brand) return null;
  const name = typeof brand.name === 'string' ? brand.name.trim() : '';
  if (!name) return null;
  const enabled = brand.displayEnabled == null || brand.displayEnabled === true || Number(brand.displayEnabled) === 1;
  if (!enabled) return null;
  const kind = brand.kind === true ? 1 : Number(brand.kind ?? 0);
  return { label: kind === 1 ? 'store' : 'brand', name };
};
