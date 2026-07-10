// Admin domain models for the promotion (ad / coupon / combination) and system
// (admin / notice / log / role / storage) verticals. Ads map the litemall-db
// entity served by the gateway-edge controller; coupon/combination map
// promotion-service's manager DTOs (/srv/private/admin/promotion/**) with the
// enum display-name strings normalised back to numeric codes in
// adminPromotionApi. System CRUD stays edge-hosted; storage is served by
// litemall-goods-management. Server-managed fields are never sent on create.

import { PagedList } from './catalog.model';

export type { PagedList };

// ----- Promotion --------------------------------------------------------

export interface IAd {
  id?: number;
  name?: string;
  link?: string;
  url?: string;
  position?: number;
  content?: string;
  startTime?: string;
  endTime?: string;
  enabled?: boolean;
  addTime?: string;
  updateTime?: string;
  deleted?: boolean;
}

// Promotion-service CouponManagerDtoResponse, with couponId → id and the enum
// display strings mapped back to numeric codes (see adminPromotionApi).
export interface ICoupon {
  id?: number;
  name?: string;
  description?: string;
  tag?: string;
  total?: number;
  discount?: number;
  min?: number;
  limitPerUser?: number;
  type?: number; // 0 common, 1 register, 2 exchange code
  status?: number; // 0 normal, 1 expired, 2 used up
  goodsType?: number; // 0 all goods, 1 category, 2 specific goods
  goodsValue?: number[];
  code?: string;
  timeType?: number; // 0 relative days, 1 absolute window
  days?: number;
  startTime?: string;
  endTime?: string;
}

// Promotion-service UserCouponDtoResponse (issue record), userCouponId → id.
export interface ICouponUser {
  id?: number;
  userId?: number;
  couponId?: number;
  status?: number; // 0 usable, 1 used, 2 expired, 3 withdrawn
  usedTime?: string;
  startTime?: string;
  endTime?: string;
  orderId?: number;
}

// Promotion-service CombinationManagerDtoResponse (group-buy campaign rule),
// combinationId → id.
export interface ICombination {
  id?: number;
  goodsId?: number;
  title?: string;
  picUrl?: string;
  combinationPrice?: number;
  originalPrice?: number;
  requiredMembers?: number;
  limitPerUser?: number;
  status?: number; // 0 draft, 1 active, 2 expired, 3 offline
  startTime?: string;
  endTime?: string;
}

// Promotion-service CombinationPinkDtoResponse: one running/finished group
// (leader slot) of a combination campaign.
export interface ICombinationPink {
  pinkId?: number;
  combinationId?: number;
  userId?: number;
  orderId?: number;
  requiredMembers?: number;
  memberCount?: number;
  expireTime?: string;
  status?: number; // 0 pending, 1 success, 2 failed
}

// ----- System -----------------------------------------------------------

export interface IAdmin {
  id?: number;
  username?: string;
  password?: string; // write-only on create; never returned
  avatar?: string;
  roleIds?: number[];
  lastLoginIp?: string;
  lastLoginTime?: string;
  addTime?: string;
  updateTime?: string;
  deleted?: boolean;
}

export interface INotice {
  id?: number;
  title?: string;
  content?: string;
  adminId?: number;
  addTime?: string;
  updateTime?: string;
  deleted?: boolean;
}

export interface ILog {
  id?: number;
  admin?: string;
  ip?: string;
  type?: number;
  action?: string;
  status?: boolean;
  result?: string;
  comment?: string;
  addTime?: string;
}

export interface IRole {
  id?: number;
  name?: string;
  desc?: string;
  enabled?: boolean;
  addTime?: string;
  updateTime?: string;
  deleted?: boolean;
}

export interface IRoleOption {
  value: number;
  label: string;
}

export interface IStorage {
  id?: number;
  key?: string;
  name?: string;
  type?: string;
  size?: number;
  url?: string;
  addTime?: string;
  updateTime?: string;
  deleted?: boolean;
}
