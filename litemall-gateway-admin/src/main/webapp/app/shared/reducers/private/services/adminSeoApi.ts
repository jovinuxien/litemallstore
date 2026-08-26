import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';

// RTK Query client for the on-page SEO admin surface, served by goods-management
// under '/srv/private/admin/seo' (the admin gateway's '/srv/**' catch-all — no
// route change). Coded to the {errno,errmsg,data} envelope every litemall admin
// endpoint uses.
//
// `monthlySearches` is `null` when the provider returned a term without a volume.
// null means UNKNOWN, never 0 — a term with unknown demand is not a term with no
// demand, so the view renders those as an explicit '—' rather than a zero that
// would sort it last as if it were worthless.

export interface ApiEnvelope<T> {
  errno: number;
  errmsg?: string;
  data: T;
}

export interface ISeoTerm {
  term: string;
  monthlySearches: number | null;
}

export interface ISeoTitleRow {
  goodsId: number;
  category: string | null;
  currentTitle: string;
  currentLength: number;
  proposedTitle: string;
  proposedLength: number;
  // True when a human should look before applying: the shortened title dropped the
  // matched keyword, or truncation would have mangled the wording, or there is
  // nothing to fix. The list badges these and excludes them from "apply all".
  needsReview: boolean;
  matchedKeyword: string | null;
  matchedKeywordVolume: number | null;
  keywordSurvives: boolean;
  terms: ISeoTerm[];
}

export interface ISeoTitlesResponse {
  // Over-length products matching the filter, across all pages.
  total: number;
  // Live products examined — the denominator for "how bad is it".
  scanned: number;
  maxLength: number;
  list: ISeoTitleRow[];
}

export interface SeoTitlesParams {
  maxLength?: number;
  categoryId?: number;
  page: number;
  limit: number;
}

const EMPTY: ISeoTitlesResponse = { total: 0, scanned: 0, maxLength: 60, list: [] };

export const adminSeoApi = createApi({
  reducerPath: 'adminSeoApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin/seo',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
  tagTypes: ['SeoTitles'],
  endpoints: builder => ({
    // Longest titles first — the worst offenders are where the attention pays off.
    getSeoTitles: builder.query<ISeoTitlesResponse, SeoTitlesParams>({
      query: ({ maxLength, categoryId, page, limit }) => ({
        url: '/titles',
        params: { ...(maxLength ? { maxLength } : {}), ...(categoryId ? { categoryId } : {}), page, limit },
      }),
      transformResponse: (r: ApiEnvelope<ISeoTitlesResponse>) => r?.data ?? EMPTY,
      providesTags: ['SeoTitles'],
    }),
    // Applies EXACTLY the title passed in, which may be the editor's amendment rather
    // than the server's proposal — re-deriving it here would make the confirmation the
    // administrator read a lie.
    applySeoTitle: builder.mutation<ApiEnvelope<unknown>, { goodsId: number; title: string }>({
      query: body => ({ url: '/titles/apply', method: 'POST', body }),
      invalidatesTags: ['SeoTitles'],
    }),
  }),
});

export const { useGetSeoTitlesQuery, useApplySeoTitleMutation } = adminSeoApi;
