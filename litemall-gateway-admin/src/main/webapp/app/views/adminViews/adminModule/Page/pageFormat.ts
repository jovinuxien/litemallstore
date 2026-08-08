import { PageCategory } from 'app/shared/reducers/private/services/adminContentApi';
import { ElTag } from 'app/views/adminViews/adminModule/_shared/crudUi';

// Wave 20: shared pure helpers for the DIY-page category/template surfaces
// (PageList filters + columns, PageEditor category select, Postiz page
// source). Kept free of React so jest covers them directly.

export const PAGE_CATEGORIES: { value: PageCategory; label: string }[] = [
  { value: 'general', label: 'General' },
  { value: 'coupon', label: 'Coupon' },
  { value: 'groupon', label: 'Groupon' },
];

/** Pre-V54 rows may not carry the field — the server default is 'general'. */
export const normalizeCategory = (category?: string | null): PageCategory =>
  category === 'coupon' || category === 'groupon' ? category : 'general';

export const categoryLabel = (category?: string | null): string => {
  const c = normalizeCategory(category);
  return PAGE_CATEGORIES.find(x => x.value === c)?.label ?? 'General';
};

/** Tag color per category — coupon/groupon pop, general stays neutral. */
export const categoryTag = (category?: string | null): ElTag => {
  switch (normalizeCategory(category)) {
    case 'coupon':
      return 'danger';
    case 'groupon':
      return 'warning';
    default:
      return 'info';
  }
};

/**
 * Pull the freshly cloned page id out of the clone-mutation envelope.
 * Non-zero errno or a missing row ⇒ null (the caller surfaces errmsg instead).
 */
export const clonedPageId = (env: unknown): number | null => {
  const e = env as { errno?: number; data?: { id?: unknown } } | undefined;
  if (!e || e.errno !== 0) return null;
  const id = e.data?.id;
  return typeof id === 'number' && Number.isFinite(id) ? id : null;
};
