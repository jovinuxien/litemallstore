import { baseAxios, SRV, unwrap } from './http';

/**
 * Customer self-service domain: profile, address book, favorites (collect),
 * footprint (browsing history), coupons, feedback, product comments.
 *
 * Live on `/srv`: address (order-service), collect/footprint/feedback/comment
 * (goods-management, 2026-07-07), coupon (promotion-service legacy-parity
 * surface, 2026-07-07). Still pending: `/srv/user/index|profile` — no owning
 * service yet; those views keep the isMissingEndpoint guard. Tracked in
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

export interface ICommentUserInfo {
  nickName?: string;
  avatarUrl?: string;
}

/**
 * One product review as /srv/comment/list returns it: local rows serialise addTime as a
 * LocalDateTime number[] tuple; CJ-proxied reviews carry an ISO string.
 */
export interface IComment {
  content?: string;
  adminContent?: string | null;
  star?: number;
  userInfo?: ICommentUserInfo;
  picList?: string[];
  addTime?: string | number[];
}

/**
 * Review-submission body for POST /srv/comment/post (mirrors the legacy
 * litemall wx contract / LitemallComment row): type 0 = goods review,
 * valueId = goods id (numeric or cj_<pid>), star 1..5.
 */
export interface ICommentPost {
  type: number;
  valueId: number | string;
  star: number;
  content: string;
  hasPicture?: boolean;
  picUrls?: string[];
}

export interface PageParams {
  page?: number;
  limit?: number;
}

export const userApi = {
  // TODO(/srv follow-up: user) — profile/order-stat hub.
  index: () => unwrap(baseAxios.get(`${SRV}/user/index`)),
  profileUpdate: (body: unknown) => unwrap(baseAxios.post(`${SRV}/user/profile`, body)),

  // Address book CRUD — LIVE on the order service (LitemallAddressController,
  // /srv/address/*), routed via the gateway customer-order predicate. Buyer is
  // bound from the gateway-injected X-User-Id.
  addressList: () => unwrap<IAddress[]>(baseAxios.get(`${SRV}/address/list`)),
  addressDetail: (id: number) => unwrap<IAddress>(baseAxios.get(`${SRV}/address/detail?id=${id}`)),
  addressSave: (body: IAddress) => unwrap(baseAxios.post(`${SRV}/address/save`, body)),
  addressDelete: (id: number) => unwrap(baseAxios.post(`${SRV}/address/delete`, { id })),

  // TODO(/srv follow-up: user) — favorites (collect). type 0=goods, 1=topics.
  collectList: (type = 0, params: PageParams = {}) =>
    unwrap(baseAxios.get(`${SRV}/collect/list`, { params: { type, ...params } })),
  collectToggle: (type: number, valueId: number | string) =>
    unwrap(baseAxios.post(`${SRV}/collect/addordelete`, { type, valueId })),

  // TODO(/srv follow-up: goods-management) — footprint (browsing history).
  // record is fired by the PDP on view; the backend dedupes same goods/day
  // (contract: docs/handoff-goods-management-engagement.md).
  footprintList: (params: PageParams = {}) => unwrap(baseAxios.get(`${SRV}/footprint/list`, { params })),
  footprintRecord: (goodsId: number | string) => unwrap(baseAxios.post(`${SRV}/footprint/record`, { goodsId })),
  footprintDelete: (id: number) => unwrap(baseAxios.post(`${SRV}/footprint/delete`, { id })),

  // Coupons — the promotion service serves these legacy paths natively
  // (fix/promotion interfaces/rest/legacy/LitemallCouponLegacyController;
  // contract: litemall-promotion-service/docs/spec-gateway-routes.md), routed
  // via the gateway customer-promotion predicate. Live once fix/promotion
  // merges and the service runs. mylist/selectlist items carry
  // id = userCouponId (the redeem handle) and cid = couponId.
  couponList: (params: PageParams = {}) => unwrap<{ list: ICoupon[] }>(baseAxios.get(`${SRV}/coupon/list`, { params })),
  couponMyList: (status = 0, params: PageParams = {}) =>
    unwrap<{ list: ICoupon[] }>(baseAxios.get(`${SRV}/coupon/mylist`, { params: { status, ...params } })),
  // Usable-for-this-checkout: the caller passes the cart facts directly —
  // promotion has no cart access, so amount + numeric goods/category id CSVs
  // replace the legacy cartId/grouponRulesId params.
  couponSelectList: (amount: number, goodsIds: number[] = [], categoryIds: number[] = []) =>
    unwrap<ICoupon[]>(
      baseAxios.get(`${SRV}/coupon/selectlist`, {
        params: {
          amount,
          goodsIds: goodsIds.length ? goodsIds.join(',') : undefined,
          categoryIds: categoryIds.length ? categoryIds.join(',') : undefined,
        },
      })
    ),
  couponReceive: (couponId: number) => unwrap(baseAxios.post(`${SRV}/coupon/receive`, { couponId })),
  couponExchange: (code: string) => unwrap(baseAxios.post(`${SRV}/coupon/exchange`, { code })),

  // TODO(/srv follow-up: user) — feedback.
  feedbackSubmit: (body: unknown) => unwrap(baseAxios.post(`${SRV}/feedback/submit`, body)),

  // Product comments/reviews — LIVE on goods-management (/srv/comment/*, public).
  // valueId is a numeric goods id or the cj_<pid> doc id; CJ-sourced goods are served
  // their CJ reviews transparently. showType 0 = all (1 = with-picture, local only).
  commentCount: (valueId: number | string, type = 0) =>
    unwrap<{ allCount: number; hasPicCount: number }>(baseAxios.get(`${SRV}/comment/count`, { params: { valueId, type } })),
  commentList: (valueId: number | string, type = 0, params: PageParams = {}) =>
    unwrap<{ list: IComment[]; total: number }>(
      baseAxios.get(`${SRV}/comment/list`, { params: { valueId, type, showType: 0, ...params } })
    ),
  // TODO(/srv follow-up: goods-management) — post a review. Authenticated
  // (buyer from X-User-Id); this request shape is the contract goods-management
  // builds to (docs/handoff-goods-management-engagement.md).
  commentPost: (body: ICommentPost) => unwrap(baseAxios.post(`${SRV}/comment/post`, body)),
};
