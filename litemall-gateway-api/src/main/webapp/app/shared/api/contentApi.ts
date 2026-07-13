import { baseAxios, SRV, unwrap } from './http';

/**
 * Public content domain: brands, topics (specials), groupons. These are the
 * "public endpoints we will move from /wx onto /srv in litemall-goods-management"
 * — they are NOT on `/srv` yet. Each call targets the agreed `/srv` path and
 * 404s gracefully until goods-management ships them (docs/SRV-FOLLOWUPS.md).
 * No `/wx`.
 */
export interface IBrand {
  id?: number;
  name?: string;
  desc?: string;
  picUrl?: string;
  floorPrice?: number;
}

export interface ITopic {
  id?: number;
  title?: string;
  subtitle?: string;
  content?: string;
  price?: number;
  readCount?: string;
  picUrl?: string;
}

/** One group-buy card as /srv/groupon/list returns it (legacy GrouponItem shape). */
export interface IGrouponItem {
  id?: number;
  goodsId?: number;
  goodsName?: string;
  picUrl?: string;
  retailPrice?: number;
  grouponPrice?: number;
  discount?: number;
  discountMember?: number;
}

export interface PageParams {
  page?: number;
  limit?: number;
  sort?: string;
  order?: 'asc' | 'desc';
}

/** Article list row / detail — /srv/article/* (goods-management Wave 4). */
export interface IArticle {
  id: number;
  categoryId?: number;
  categoryName?: string;
  title?: string;
  summary?: string;
  picUrl?: string;
  isHot?: boolean;
  isBanner?: boolean;
  goodsId?: number;
  viewCount?: number;
  addTime?: string | number[];
  /** Detail only — server-sanitized HTML, safe to inject. */
  content?: string;
}

export interface IArticleCategory {
  id: number;
  name?: string;
  sortOrder?: number;
}

/** One palette-v1 component (spec-page-palette-v1.md §1). */
export interface IPageComponent {
  type: string;
  key?: string;
  config: Record<string, unknown>;
}

/** PageView — /srv/page/home | /srv/page/{id} (spec §4). */
export interface IPageView {
  id: number;
  name?: string;
  position?: 'home' | 'custom';
  components: IPageComponent[];
  updateTime?: string | number[];
}

/** Region row — /srv/region/list (BARE ARRAY, upstream WxRegionController shape). */
export interface IRegion {
  id: number;
  pid?: number;
  name: string;
  type?: number;
  code?: number;
}

/** Region tree node — /srv/region/clist (legacy RegionVo, 3 levels). */
export interface IRegionNode {
  id: number;
  name: string;
  code?: number;
  children?: IRegionNode[];
}

export const contentApi = {
  // TODO(/srv follow-up: goods-management) — brand list/detail.
  brandList: (params: PageParams = {}) => unwrap<{ list: IBrand[]; total: number }>(baseAxios.get(`${SRV}/brand/list`, { params })),
  brandDetail: (id: number | string) => unwrap<IBrand>(baseAxios.get(`${SRV}/brand/detail?id=${encodeURIComponent(String(id))}`)),

  // TODO(/srv follow-up: goods-management) — topics.
  topicList: (params: PageParams = {}) => unwrap<{ list: ITopic[]; total: number }>(baseAxios.get(`${SRV}/topic/list`, { params })),
  topicDetail: (id: number | string) => unwrap<{ topic: ITopic; goods: unknown[] }>(baseAxios.get(`${SRV}/topic/detail?id=${encodeURIComponent(String(id))}`)),
  topicRelated: (id: number | string) => unwrap<{ list: ITopic[] }>(baseAxios.get(`${SRV}/topic/related?id=${encodeURIComponent(String(id))}`)),

  // Group-buy browse — the promotion service serves this legacy path natively
  // (fix/promotion LitemallGrouponLegacyController; live once that branch
  // merges and the service runs). The interactive flow (start/join/my groups)
  // uses the canonical /srv/promotion/combination surface in promotionApi.ts.
  grouponList: (params: PageParams = {}) =>
    unwrap<{ list: IGrouponItem[]; total: number }>(baseAxios.get(`${SRV}/groupon/list`, { params })),

  // Article CMS — goods-management Wave 4 (handoff-content-endpoints.md §1).
  // Callers degrade via isMissingEndpoint until that branch merges + runs.
  articleList: (page = 1, limit = 10, categoryId?: number, hotOnly?: boolean) =>
    unwrap<{ list: IArticle[]; total: number }>(
      baseAxios.get(`${SRV}/article/list`, { params: { page, limit, categoryId, hotOnly: hotOnly || undefined } })
    ),
  /** errno 643 (ApiError) = article missing/hidden. */
  articleDetail: (id: number | string) => unwrap<IArticle>(baseAxios.get(`${SRV}/article/detail?id=${encodeURIComponent(String(id))}`)),
  articleCategories: () => unwrap<{ list: IArticleCategory[]; total: number }>(baseAxios.get(`${SRV}/article/categories`)),

  // DIY pages — spec-page-palette-v1.md §4. errno 642 (ApiError) = no active
  // home / page not active; the SPA falls back to the legacy home.
  pageHome: () => unwrap<IPageView>(baseAxios.get(`${SRV}/page/home`)),
  pageById: (id: number | string) => unwrap<IPageView>(baseAxios.get(`${SRV}/page/${encodeURIComponent(String(id))}`)),

  // Region cascade — handoff-content-endpoints.md §3. BOTH return data as a
  // BARE ARRAY (no {list,total} wrapper); unwrap() passes bare payloads through.
  regionList: (pid: number) => unwrap<IRegion[]>(baseAxios.get(`${SRV}/region/list`, { params: { pid } })),
  regionCList: () => unwrap<IRegionNode[]>(baseAxios.get(`${SRV}/region/clist`)),
};
