// Admin domain models for the promotion (ad / coupon / groupon) and system
// (admin / notice / log / role / storage) verticals. These map the litemall-db
// entities served under /srv/private/admin/* — the promotion/system CRUD is
// hosted at the gateway edge (litemall-gatewayadmin/web/admin/*) over the same
// db services the legacy admin-api used; storage is served by
// litemall-goods-management. Server-managed fields (addTime/updateTime/deleted)
// are optional and never sent on create.

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

export interface ICoupon {
  id?: number;
  name?: string;
  desc?: string;
  tag?: string;
  total?: number;
  discount?: number;
  min?: number;
  limit?: number;
  type?: number; // 0 common, 1 register, 2 code
  status?: number; // 0 normal, 1 expired, 2 out
  goodsType?: number;
  goodsValue?: number[];
  code?: string;
  timeType?: number; // 0 fixed range, 1 relative days
  days?: number;
  startTime?: string;
  endTime?: string;
  addTime?: string;
  updateTime?: string;
  deleted?: boolean;
}

export interface ICouponUser {
  id?: number;
  userId?: number;
  couponId?: number;
  status?: number;
  usedTime?: string;
  startTime?: string;
  endTime?: string;
  orderId?: number;
  addTime?: string;
}

export interface IGrouponRule {
  id?: number;
  goodsId?: number;
  goodsName?: string;
  picUrl?: string;
  discount?: number;
  discountMember?: number;
  expireTime?: string;
  status?: number; // 0 on, 1 expired-down, 2 admin-down
  addTime?: string;
  updateTime?: string;
  deleted?: boolean;
}

// A running groupon activity record (from /groupon/listRecord): the groupon
// head plus its joined sub-groupons and the resolved rule/goods.
export interface IGrouponRecord {
  groupon?: {
    id?: number;
    orderId?: number;
    userId?: number;
    creatorUserId?: number;
    status?: number;
    addTime?: string;
  };
  subGroupons?: unknown[];
  rules?: IGrouponRule;
  goods?: { id?: number; name?: string; picUrl?: string };
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
