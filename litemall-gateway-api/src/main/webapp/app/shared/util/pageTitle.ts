/**
 * Client-side companion to the edge's Wave-13 head injection: a full page load
 * arrives with a route-specific <title> already injected, but client-side
 * navigations keep the previous one — so views with a natural name (PDP,
 * category landing) set it here and restore the sitewide default on unmount.
 * DEFAULT_TITLE mirrors webapp/public/index.html's <title>.
 */
const DEFAULT_TITLE = 'Trovemo — Home, Garden & DIY essentials, delivered';

export const setPageTitle = (title?: string | null): void => {
  document.title = title ? `${title} | Trovemo` : DEFAULT_TITLE;
};

export const resetPageTitle = (): void => {
  document.title = DEFAULT_TITLE;
};
