import { IPageSummary } from 'app/shared/reducers/private/services/adminContentApi';
import { IPostizLogRow, PostizPageCommand } from 'app/shared/reducers/private/services/postizApi';
import { normalizeCategory } from 'app/views/adminViews/adminModule/Page/pageFormat';

// Wave 20: pure helpers behind the Postiz source picker (Products | DIY page).
// The page source posts ONE promotion of a DIY page ({pageId, channelIds[],
// startTime} against the SAME /preview + /publish endpoints); groupon-category
// pages are REFUSED server-side with a typed errno that the panel shows
// verbatim — the notes here are only a courtesy heads-up, never a gate.

/** Default start = the next full local hour, as a datetime-local value. */
export const nextFullHourLocal = (): string => {
  const d = new Date();
  d.setMinutes(0, 0, 0);
  d.setHours(d.getHours() + 1);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
};

/** Build the page-source body. startLocal is a datetime-local value; the wire format is UTC ISO. */
export const pageCommand = (pageId: number, channelIds: Iterable<string>, startLocal: string): PostizPageCommand => ({
  pageId,
  channelIds: [...channelIds],
  startTime: new Date(startLocal).toISOString(),
});

/** The preview/publish inputs that must not drift between Preview and Publish. */
export const pageSignature = (pageId: number | null, channelIds: Iterable<string>, startLocal: string): string =>
  JSON.stringify([pageId, [...channelIds].sort(), startLocal]);

/**
 * Courtesy notes for the selected page. The SERVER is the authority (active
 * resolution + the groupon-category refusal) — these only set expectations.
 */
export const pageSourceNotes = (page?: Pick<IPageSummary, 'status' | 'category'> | null): string[] => {
  if (!page) return [];
  const notes: string[] = [];
  if (page.status !== 'active') {
    notes.push('Only ACTIVE pages can be published — activate this page first, otherwise the server will refuse it.');
  }
  if (normalizeCategory(page.category) === 'groupon') {
    notes.push('Groupon-category pages are currently refused for social publishing (held back until group-buy checkout ships).');
  }
  return notes;
};

/** History-row subject: page rows link to the page editor, goods rows to the insight view. */
export const logSubject = (row: Pick<IPostizLogRow, 'goodsId' | 'pageId'>): { kind: 'page' | 'goods'; to: string; label: string } | null => {
  if (row.pageId != null) return { kind: 'page', to: `/admin/mall/page/${row.pageId}`, label: `Page #${row.pageId}` };
  if (row.goodsId != null) return { kind: 'goods', to: `/admin/goods/${row.goodsId}/insight`, label: `Goods #${row.goodsId}` };
  return null;
};
