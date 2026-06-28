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

export interface PageParams {
  page?: number;
  limit?: number;
  sort?: string;
  order?: 'asc' | 'desc';
}

export const contentApi = {
  // TODO(/srv follow-up: goods-management) — brand list/detail.
  brandList: (params: PageParams = {}) => unwrap<{ list: IBrand[]; total: number }>(baseAxios.get(`${SRV}/brand/list`, { params })),
  brandDetail: (id: number | string) => unwrap<IBrand>(baseAxios.get(`${SRV}/brand/detail?id=${encodeURIComponent(String(id))}`)),

  // TODO(/srv follow-up: goods-management) — topics.
  topicList: (params: PageParams = {}) => unwrap<{ list: ITopic[]; total: number }>(baseAxios.get(`${SRV}/topic/list`, { params })),
  topicDetail: (id: number | string) => unwrap<{ topic: ITopic; goods: unknown[] }>(baseAxios.get(`${SRV}/topic/detail?id=${encodeURIComponent(String(id))}`)),
  topicRelated: (id: number | string) => unwrap<{ list: ITopic[] }>(baseAxios.get(`${SRV}/topic/related?id=${encodeURIComponent(String(id))}`)),

  // TODO(/srv follow-up: promotion) — groupon list.
  grouponList: (params: PageParams = {}) => unwrap<{ list: unknown[]; total: number }>(baseAxios.get(`${SRV}/groupon/list`, { params })),
};
