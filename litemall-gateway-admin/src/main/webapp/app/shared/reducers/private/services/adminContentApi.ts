import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';

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

const emptyList = <T>(): ListData<T> => ({ list: [], total: 0 });

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
      transformResponse: (r: ApiEnvelope<ListData<IArticle>>) => r?.data ?? emptyList<IArticle>(),
      providesTags: ['Article'],
    }),
    readArticle: builder.query<IArticle, number | string>({
      query: id => ({ url: '/article/read', params: { id } }),
      transformResponse: (r: ApiEnvelope<IArticle>) => r?.data ?? {},
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
      transformResponse: (r: ApiEnvelope<ListData<IArticleCategory>>) => r?.data?.list ?? [],
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
      query: ({ page, limit, position, status }) => ({
        url: '/page/list',
        params: clean({ page, limit, position, status }),
      }),
      transformResponse: (r: ApiEnvelope<ListData<IPageSummary>>) => r?.data ?? emptyList<IPageSummary>(),
      providesTags: ['Page'],
    }),
    // Any status — this is also the draft preview read (config = parsed object).
    readPage: builder.query<IPageDetail | undefined, number | string>({
      query: id => ({ url: '/page/read', params: { id } }),
      transformResponse: (r: ApiEnvelope<IPageDetail>) => r?.data,
    }),
    // Machine-readable component schema — the editor is generated from THIS.
    getPagePalette: builder.query<IPalette | undefined, void>({
      query: () => ({ url: '/page/palette' }),
      transformResponse: (r: ApiEnvelope<IPalette>) => r?.data,
    }),
    // Always created as draft; palette violations → errno 640 (errmsg names
    // the offending component — surface verbatim). Returns the created page.
    createPage: builder.mutation<ApiEnvelope<IPageDetail>, { name: string; position: PagePosition; config: IPageConfig }>({
      query: body => ({ url: '/page/create', method: 'POST', body }),
      invalidatesTags: ['Page'],
    }),
    // Position is immutable after create; re-validates config → 640.
    updatePage: builder.mutation<ApiEnvelope, { id: number; name?: string; config?: IPageConfig }>({
      query: body => ({ url: '/page/update', method: 'POST', body }),
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
  useActivatePageMutation,
  useDeactivatePageMutation,
  useDeletePageMutation,
} = adminContentApi;
