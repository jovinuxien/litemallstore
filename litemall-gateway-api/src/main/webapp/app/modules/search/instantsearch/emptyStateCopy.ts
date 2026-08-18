/**
 * Wave 26 — what the zero-results state is allowed to say.
 *
 * Narrowing the storefront to the anchor cluster left the twelve retired L1
 * departments reachable by URL (old bookmarks, Google's pre-narrowing index,
 * the breadcrumb of an off-sale product). Those land on `/category/:id` with
 * zero hits, where "No products found. Check the spelling" is nonsense — there
 * was no spelling.
 *
 * The distinction that matters is WHY the page is empty, and it is only honest
 * to blame the department when nothing else narrowed the results: a live
 * category filtered down to zero by a price refinement is a filter miss, not a
 * retired department.
 */
export interface EmptyStateContext {
  /** Set on the /category/:id landing; absent on plain /search. */
  categoryId?: string | null;
  query?: string | null;
  /** Any facet/price/toggle refinement is active. */
  refined?: boolean;
}

export interface EmptyStateCopy {
  title: string;
  body: string;
}

/**
 * A refinement container holds attribute -> value; an emptied facet leaves the
 * container in place with nothing selected, so the check has to reach the leaf.
 */
const isRefinement = (value: unknown): boolean => {
  if (value == null || value === false) return false;
  if (Array.isArray(value)) return value.some(v => isRefinement(v));
  if (typeof value === 'object') return Object.values(value as Record<string, unknown>).some(isRefinement);
  if (typeof value === 'string') return value.trim() !== '';
  return true;
};

/** True when the UI state carries a refinement of any kind (query excluded). */
export const hasRefinements = (uiState: Record<string, unknown> | null | undefined): boolean =>
  ['refinementList', 'menu', 'hierarchicalMenu', 'numericMenu', 'range', 'toggle'].some(k => isRefinement(uiState?.[k]));

export const emptyStateCopy = ({ categoryId, query, refined }: EmptyStateContext): EmptyStateCopy => {
  const q = (query ?? '').trim();
  if (q) {
    return {
      title: `No results for “${q}”`,
      body: 'Check the spelling or try a different term — or start from one of these.',
    };
  }
  if (refined) {
    return {
      title: 'No products match these filters',
      body: 'Clear a filter to widen the results — or start from one of these.',
    };
  }
  if (categoryId) {
    // Says only what is true of every empty department, retired or simply
    // sold out, and never claims a delivery or restock we have not measured.
    return {
      title: 'Nothing in this department right now',
      body: 'Trovemo now focuses on home, garden and tools — here is what we stock today.',
    };
  }
  return {
    title: 'No products found',
    body: 'Check the spelling or try a different term — or start from one of these.',
  };
};
