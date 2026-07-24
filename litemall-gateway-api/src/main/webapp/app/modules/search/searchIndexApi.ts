import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';

/**
 * Search-box chrome data from goods-management's `GET /srv/search/index`
 * (litemall-wx-api parity): the curated default keyword, the hot ("trending")
 * keywords, and the caller's own search history — history comes back empty for
 * anonymous visitors. Shared by the header search dropdown (Layout) and the
 * zero-results state on /search.
 *
 * Wire shape: `{defaultKeyword, historyKeywordList[], hotKeywordList[]}` where
 * each entry is a LitemallKeyword / LitemallSearchHistory row carrying a
 * `.keyword` string. Normalised here to plain string lists so the components
 * never see the row shapes.
 */
export interface ISearchIndexData {
  defaultKeyword: string | null;
  historyKeywords: string[];
  hotKeywords: string[];
}

const keywordText = (raw: unknown): string => {
  if (typeof raw === 'string') return raw.trim();
  const k = (raw as { keyword?: unknown } | null)?.keyword;
  return typeof k === 'string' ? k.trim() : '';
};

// Case-insensitive dedupe, first occurrence wins (history rows repeat per search).
const dedupe = (list: string[]): string[] => {
  const seen = new Set<string>();
  return list.filter(k => {
    const key = k.toLowerCase();
    if (!k || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};

export const fetchSearchIndex = async (): Promise<ISearchIndexData> => {
  const res = await baseAxios.get(`${BASE_URL_CONTEXT}/search/index`);
  const d = res.data?.data ?? res.data ?? {};
  const history = Array.isArray(d.historyKeywordList) ? d.historyKeywordList : [];
  const hots = Array.isArray(d.hotKeywordList) ? d.hotKeywordList : [];
  return {
    defaultKeyword: keywordText(d.defaultKeyword) || null,
    historyKeywords: dedupe(history.map(keywordText)),
    hotKeywords: dedupe(hots.map(keywordText)),
  };
};

/** Clears the signed-in caller's search history (POST — the backend guards anonymous). */
export const clearSearchHistory = async (): Promise<void> => {
  await baseAxios.post(`${BASE_URL_CONTEXT}/search/clearhistory`);
};
