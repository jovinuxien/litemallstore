import { baseAxios, SRV, unwrap } from './http';

/**
 * Customer self-service domain: profile, address book, favorites (collect),
 * footprint (browsing history), coupons, feedback, product comments.
 *
 * NONE of these are on `/srv` yet — they currently only exist on the legacy
 * `/wx` route which this SPA does not consume. Each call below targets the
 * AGREED `/srv` path; until the owning worktree ships it, calls 404 and the
 * views render graceful empty states (see isMissingEndpoint). Tracked in
 * docs/SRV-FOLLOWUPS.md.
 */
export interface IAddress {
  id?: number;
  name?: string;
  tel?: string;
  province?: string;
  city?: string;
  county?: string;
  areaCode?: string;
  postalCode?: string;
  addressDetail?: string;
  isDefault?: boolean;
}

export interface ICoupon {
  id?: number;
  cid?: number;
  name?: string;
  desc?: string;
  tag?: string;
  discount?: number;
  min?: number;
  type?: number;
  status?: number;
  available?: boolean;
  startTime?: string;
  endTime?: string;
}

export interface IComment {
  id?: number;
  content?: string;
  adminContent?: string;
  star?: number;
  nickName?: string;
  avatar?: string;
  picList?: string[];
  addTime?: string;
}

export interface PageParams {
  page?: number;
  limit?: number;
}

export const userApi = {
  // TODO(/srv follow-up: user) — profile/order-stat hub.
  index: () => unwrap(baseAxios.get(`${SRV}/user/index`)),
  profileUpdate: (body: unknown) => unwrap(baseAxios.post(`${SRV}/user/profile`, body)),

  // TODO(/srv follow-up: order|user) — address book CRUD.
  addressList: () => unwrap<IAddress[]>(baseAxios.get(`${SRV}/address/list`)),
  addressDetail: (id: number) => unwrap<IAddress>(baseAxios.get(`${SRV}/address/detail?id=${id}`)),
  addressSave: (body: IAddress) => unwrap(baseAxios.post(`${SRV}/address/save`, body)),
  addressDelete: (id: number) => unwrap(baseAxios.post(`${SRV}/address/delete`, { id })),

  // TODO(/srv follow-up: user) — favorites (collect). type 0=goods, 1=topics.
  collectList: (type = 0, params: PageParams = {}) =>
    unwrap(baseAxios.get(`${SRV}/collect/list`, { params: { type, ...params } })),
  collectToggle: (type: number, valueId: number | string) =>
    unwrap(baseAxios.post(`${SRV}/collect/addordelete`, { type, valueId })),

  // TODO(/srv follow-up: user) — footprint (browsing history).
  footprintList: (params: PageParams = {}) => unwrap(baseAxios.get(`${SRV}/footprint/list`, { params })),
  footprintDelete: (id: number) => unwrap(baseAxios.post(`${SRV}/footprint/delete`, { id })),

  // TODO(/srv follow-up: order|promotion) — coupons.
  couponList: (params: PageParams = {}) => unwrap<{ list: ICoupon[] }>(baseAxios.get(`${SRV}/coupon/list`, { params })),
  couponMyList: (status = 0, params: PageParams = {}) =>
    unwrap<{ list: ICoupon[] }>(baseAxios.get(`${SRV}/coupon/mylist`, { params: { status, ...params } })),
  couponSelectList: (cartId?: number, grouponRulesId?: number) =>
    unwrap<ICoupon[]>(baseAxios.get(`${SRV}/coupon/selectlist`, { params: { cartId, grouponRulesId } })),
  couponReceive: (couponId: number) => unwrap(baseAxios.post(`${SRV}/coupon/receive`, { couponId })),

  // TODO(/srv follow-up: user) — feedback.
  feedbackSubmit: (body: unknown) => unwrap(baseAxios.post(`${SRV}/feedback/submit`, body)),

  // TODO(/srv follow-up: goods-management) — product comments/reviews.
  commentCount: (valueId: number | string, type = 0) =>
    unwrap<{ allCount: number; hasPicCount: number }>(baseAxios.get(`${SRV}/comment/count`, { params: { valueId, type } })),
  commentList: (valueId: number | string, type = 0, params: PageParams = {}) =>
    unwrap<{ data: IComment[]; count: number }>(baseAxios.get(`${SRV}/comment/list`, { params: { valueId, type, ...params } })),
};
