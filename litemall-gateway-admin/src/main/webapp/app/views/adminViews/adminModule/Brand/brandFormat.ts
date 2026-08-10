import { IBrand } from 'app/shared/model/admin/catalog.model';
import { ElTag } from 'app/views/adminViews/adminModule/_shared/crudUi';

// Pure presentation/payload helpers for the Wave-25 brand curation surface.
// Every field is optional on the wire: rows from a pre-V60 backend carry none
// of source/kind/displayEnabled and must render as an ordinary enabled manual
// brand. The legacy V23 column default is 'local' — treated as manual.

const MANUAL_SOURCES = ['', 'manual', 'local'];

export const isProviderRow = (brand: Pick<IBrand, 'source'>): boolean =>
  !MANUAL_SOURCES.includes((brand.source ?? '').trim().toLowerCase());

export const sourceLabel = (source?: string | null): string => {
  const s = (source ?? '').trim().toLowerCase();
  if (MANUAL_SOURCES.includes(s)) return 'Manual';
  if (s === 'cj-supplier') return 'CJ supplier';
  return source!.trim();
};

// kind 1 = supplier store, anything else (incl. absent) = consumer brand.
export const kindLabel = (kind?: number | null): { label: 'Store' | 'Brand'; tone: ElTag } =>
  Number(kind) === 1 ? { label: 'Store', tone: 'warning' } : { label: 'Brand', tone: 'primary' };

// Tolerant read of the display_enabled tinyint: boolean or 0/1; absent means
// visible (pre-V60 rows and the manual backfill are display_enabled=1).
export const isDisplayEnabled = (value?: boolean | number | null): boolean => {
  if (value == null) return true;
  return typeof value === 'boolean' ? value : Number(value) !== 0;
};

// Full-row update body with only displayEnabled flipped — rename and toggle
// both ride the existing POST /brand/update. goodsCount is a read-model field
// and never sent back.
export const toggledUpdateBody = (brand: IBrand): IBrand => {
  const { goodsCount, ...row } = brand;
  return { ...row, displayEnabled: !isDisplayEnabled(brand.displayEnabled) };
};
