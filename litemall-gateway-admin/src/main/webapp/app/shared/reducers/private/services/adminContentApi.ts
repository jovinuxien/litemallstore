import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';
import { fromServerDateTime } from 'app/shared/util/server-datetime';

// RTK Query client for the content subdomain served by litemall-goods-management
// through the gateway under '/srv/private/admin/*': the article CMS
// (AdminArticleController) and the DIY page builder (AdminPageController).
// Normative contracts:
//   litemall-goods-management/docs/handoff-content-endpoints.md   (article CMS)
//   litemall-goods-management/docs/spec-page-palette-v1.md        (palette v1)
// Envelope: {errno, errmsg, data}; business errors ride HTTP 200. Content
// errnos: 640 = PAGE_CONFIG_INVALID (errmsg NAMES the offending component —
// surface it verbatim), 641 = CONTENT_CONFLICT (activate race lost / delete
// refused on active home / category delete refused while referenced),
// 642/643 are customer-side only. Mutations therefore return the RAW envelope
// so views can run errnoMessage() and show errmsg verbatim.

export interface ApiEnvelope<T = unknown> {
  errno: number;
  errmsg: string;
  data?: T;
}

export interface ListData<T> {
  list: T[];
  total: number;
}

// ----- Article CMS ---------------------------------------------------------

export type ArticleStatus = 'published' | 'hidden';

export interface IArticle {
  id?: number;
  categoryId?: number;
  categoryName?: string | null;
  title?: string;
  summary?: string;
  picUrl?: string;
  content?: string;
  status?: ArticleStatus;
  isHot?: boolean;
  isBanner?: boolean;
  goodsId?: number;
  viewCount?: number;
  addTime?: string;
  updateTime?: string;
}

export interface IArticleCategory {
  id?: number;
  name?: string;
  sortOrder?: number;
  articleCount?: number;
  addTime?: string;
  updateTime?: string;
}

export interface ArticleListParams {
  page: number;
  limit: number;
  title?: string;
  categoryId?: number;
  status?: ArticleStatus | '';
}

// ----- DIY pages (palette v1) ----------------------------------------------

export type PagePosition = 'home' | 'custom';
export type PageStatus = 'draft' | 'active';
/** Wave 20: page merchandising category — drives filters + Postiz page publishing rules. */
export type PageCategory = 'general' | 'coupon' | 'groupon';

/** One component entry in the page config — {type, key?, config}. */
export interface IPageComponent {
  type: string;
  key?: string;
  config: Record<string, unknown>;
}

/** The page config column: {version: 1, components: [...]}. */
export interface IPageConfig {
  version: 1;
  components: IPageComponent[];
}

export interface IPageSummary {
  id: number;
  name: string;
  position: PagePosition;
  status: PageStatus;
  /** Wave 20 — 'general' | 'coupon' | 'groupon'; absent on pre-V54 rows ⇒ treat as 'general'. */
  category?: PageCategory;
  /** Wave 20 — seeded template pages (clone source for "New from template"). */
  isTemplate?: boolean;
  addTime?: string;
  updateTime?: string;
}

export interface IPageDetail extends IPageSummary {
  config: IPageConfig;
}

export interface PageListParams {
  page: number;
  limit: number;
  position?: PagePosition | '';
  status?: PageStatus | '';
  category?: PageCategory | '';
  /** 1 = templates only, 0 = non-templates only; omit for all rows. */
  template?: 0 | 1;
}

/** One field descriptor from GET /page/palette — drives the generated form. */
export interface IPaletteField {
  name: string;
  type: 'string' | 'int' | 'boolean' | 'enum' | 'int[]' | 'array';
  required: boolean;
  description?: string;
  values?: string[]; // enum
  min?: number; // int
  max?: number; // int
  default?: unknown;
  minItems?: number; // array / int[]
  maxItems?: number; // array / int[]
  itemFields?: IPaletteField[]; // array (image-row rows)
  requiredWhen?: string; // e.g. 'mode=byIds'
  sanitized?: boolean; // rich-text html
}

export interface IPaletteComponent {
  type: string;
  label: string;
  fields: IPaletteField[];
}

export interface IPalette {
  version: number;
  maxComponents: number;
  maxConfigBytes: number;
  degradeRule: string;
  components: IPaletteComponent[];
}

const clean = (params: Record<string, unknown>): Record<string, unknown> => {
  const out: Record<string, unknown> = {};
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v;
  });
  return out;
};

// goods-management serves these endpoints, and its module-wide ObjectMapper
// (litemall-core JacksonConfig's raw bean) writes LocalDateTime as numeric
// arrays [y, m, d, h, min, s] — the panels were built against ISO strings.
// Normalise every timestamp at the API layer so views keep plain-string
// rendering (same pattern as adminDealApi / insightApi / adminParityApi).
export const withDates = <T extends { addTime?: string; updateTime?: string }>(row: T): T => ({
  ...row,
  addTime: fromServerDateTime((row as Record<string, unknown>).addTime),
  updateTime: fromServerDateTime((row as Record<string, unknown>).updateTime),
});

export const listWithDates = <T extends { addTime?: string; updateTime?: string }>(d?: ListData<T>): ListData<T> => ({
  list: (d?.list ?? []).map(withDates),
  total: d?.total ?? 0,
});

export const adminContentApi = createApi({
  reducerPath: 'adminContentApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  tagTypes: ['Article', 'ArticleCategory', 'Page'],
  endpoints: builder => ({
    // ----- Articles ------------------------------------------------------
    listArticles: builder.query<ListData<IArticle>, ArticleListParams>({
      query: ({ page, limit, title, categoryId, status }) => ({
        url: '/article/list',
        params: clean({ page, limit, title, categoryId, status }),
      }),
      transformResponse: (r: ApiEnvelope<ListData<IArticle>>) => listWithDates(r?.data),
      providesTags: ['Article'],
    }),
    readArticle: builder.query<IArticle, number | string>({
      query: id => ({ url: '/article/read', params: { id } }),
      transformResponse: (r: ApiEnvelope<IArticle>) => (r?.data ? withDates(r.data) : {}),
    }),
    createArticle: builder.mutation<ApiEnvelope<IArticle>, IArticle>({
      query: body => ({ url: '/article/create', method: 'POST', body }),
      invalidatesTags: ['Article', 'ArticleCategory'],
    }),
    updateArticle: builder.mutation<ApiEnvelope, IArticle>({
      query: body => ({ url: '/article/update', method: 'POST', body }),
      invalidatesTags: ['Article', 'ArticleCategory'],
    }),
    deleteArticle: builder.mutation<ApiEnvelope, { id: number }>({
      query: body => ({ url: '/article/delete', method: 'POST', body }),
      invalidatesTags: ['Article', 'ArticleCategory'],
    }),

    // ----- Article categories --------------------------------------------
    // okList shape {list, total}; list rows carry per-category articleCount.
    listArticleCategories: builder.query<IArticleCategory[], void>({
      query: () => ({ url: '/article/category/list' }),
      transformResponse: (r: ApiEnvelope<ListData<IArticleCategory>>) => (r?.data?.list ?? []).map(withDates),
      providesTags: ['ArticleCategory'],
    }),
    createArticleCategory: builder.mutation<ApiEnvelope<IArticleCategory>, IArticleCategory>({
      query: body => ({ url: '/article/category/create', method: 'POST', body }),
      invalidatesTags: ['ArticleCategory'],
    }),
    updateArticleCategory: builder.mutation<ApiEnvelope, IArticleCategory>({
      query: body => ({ url: '/article/category/update', method: 'POST', body }),
      invalidatesTags: ['ArticleCategory', 'Article'],
    }),
    // Refused while articles still reference it → errno 641; surface errmsg.
    deleteArticleCategory: builder.mutation<ApiEnvelope, { id: number }>({
      query: body => ({ url: '/article/category/delete', method: 'POST', body }),
      invalidatesTags: ['ArticleCategory'],
    }),

    // ----- DIY pages -------------------------------------------------------
    listPages: builder.query<ListData<IPageSummary>, PageListParams>({
      query: ({ page, limit, position, status, category, template }) => ({
        url: '/page/list',
        params: clean({ page, limit, position, status, category, template }),
      }),
      transformResponse: (r: ApiEnvelope<ListData<IPageSummary>>) => listWithDates(r?.data),
      providesTags: ['Page'],
    }),
    // Any status — this is also the draft preview read (config = parsed object).
    readPage: builder.query<IPageDetail | undefined, number | string>({
      query: id => ({ url: '/page/read', params: { id } }),
      transformResponse: (r: ApiEnvelope<IPageDetail>) => (r?.data ? withDates(r.data) : undefined),
    }),
    // Machine-readable component schema — the editor is generated from THIS.
    getPagePalette: builder.query<IPalette | undefined, void>({
      query: () => ({ url: '/page/palette' }),
      transformResponse: (r: ApiEnvelope<IPalette>) => r?.data,
    }),
    // Always created as draft; palette violations → errno 640 (errmsg names
    // the offending component — surface verbatim). Returns the created page.
    createPage: builder.mutation<ApiEnvelope<IPageDetail>, { name: string; position: PagePosition; category?: PageCategory; config: IPageConfig }>({
      query: body => ({ url: '/page/create', method: 'POST', body }),
      invalidatesTags: ['Page'],
    }),
    // Position is immutable after create; category IS settable; re-validates config → 640.
    updatePage: builder.mutation<ApiEnvelope, { id: number; name?: string; category?: PageCategory; config?: IPageConfig }>({
      query: body => ({ url: '/page/update', method: 'POST', body }),
      invalidatesTags: ['Page'],
    }),
    // Wave 20: server-side copy — a fresh DRAFT ("Copy of …", position custom,
    // category inherited, never active). Returns the new page row.
    clonePage: builder.mutation<ApiEnvelope<IPageDetail>, { id: number }>({
      query: ({ id }) => ({ url: `/page/${id}/clone`, method: 'POST' }),
      invalidatesTags: ['Page'],
    }),
    // Transactional home swap; concurrent-activation race lost → errno 641.
    activatePage: builder.mutation<ApiEnvelope, { id: number }>({
      query: body => ({ url: '/page/activate', method: 'POST', body }),
      invalidatesTags: ['Page'],
    }),
    deactivatePage: builder.mutation<ApiEnvelope, { id: number }>({
      query: body => ({ url: '/page/deactivate', method: 'POST', body }),
      invalidatesTags: ['Page'],
    }),
    // Refuses the active home → errno 641; surface errmsg inline.
    deletePage: builder.mutation<ApiEnvelope, { id: number }>({
      query: body => ({ url: '/page/delete', method: 'POST', body }),
      invalidatesTags: ['Page'],
    }),
  }),
});

export const {
  useListArticlesQuery,
  useReadArticleQuery,
  useCreateArticleMutation,
  useUpdateArticleMutation,
  useDeleteArticleMutation,
  useListArticleCategoriesQuery,
  useCreateArticleCategoryMutation,
  useUpdateArticleCategoryMutation,
  useDeleteArticleCategoryMutation,
  useListPagesQuery,
  useReadPageQuery,
  useGetPagePaletteQuery,
  useCreatePageMutation,
  useUpdatePageMutation,
  useClonePageMutation,
  useActivatePageMutation,
  useDeactivatePageMutation,
  useDeletePageMutation,
} = adminContentApi;
