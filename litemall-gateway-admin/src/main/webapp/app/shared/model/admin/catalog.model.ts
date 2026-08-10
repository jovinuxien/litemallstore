// Admin catalog domain models, mirroring the litemall-db entities served by
// litemall-goods-management (interfaces/rest/admin/*) under /srv/private/admin/*
// (brand, category, comment, keyword, issue). Server-managed fields (addTime,
// updateTime, deleted) are optional and never sent on create.

// Generic paged list envelope produced by ResponseUtil.okList:
// { list, total, page, limit, pages }.
export interface PagedList<T> {
  list: T[];
  total: number;
  page: number;
  limit: number;
  pages: number;
}

export interface IBrand {
  id?: number;
  name?: string;
  desc?: string;
  picUrl?: string;
  sortOrder?: number;
  floorPrice?: number;
  source?: string;
  externalId?: string;
  /** 0 = consumer brand, 1 = supplier store (V60). */
  kind?: number;
  /** Server may serialize the tinyint as boolean or 0/1; absent on pre-V60 rows. */
  displayEnabled?: boolean | number;
  /** List rows only; absent until the backend serves it. */
  goodsCount?: number;
  addTime?: string;
  updateTime?: string;
  deleted?: boolean;
}

export interface IKeyword {
  id?: number;
  keyword?: string;
  url?: string;
  isHot?: boolean;
  isDefault?: boolean;
  sortOrder?: number;
  addTime?: string;
  updateTime?: string;
  deleted?: boolean;
}

export interface IIssue {
  id?: number;
  question?: string;
  answer?: string;
  addTime?: string;
  updateTime?: string;
  deleted?: boolean;
}

export interface IComment {
  id?: number;
  valueId?: number;
  type?: number;
  content?: string;
  adminContent?: string;
  userId?: number;
  hasPicture?: boolean;
  picUrls?: string[];
  star?: number;
  addTime?: string;
  updateTime?: string;
  deleted?: boolean;
}

// Create/update body — the flat LitemallCategory entity.
export interface ICategory {
  id?: number;
  name?: string;
  level?: 'L1' | 'L2';
  pid?: number;
  keywords?: string;
  desc?: string;
  iconUrl?: string;
  picUrl?: string;
  sortOrder?: number;
  source?: string;
  cjCategoryId?: string;
  addTime?: string;
  updateTime?: string;
  deleted?: boolean;
}

// The /category/list tree shape (CategoryVo): an L1 node carrying its L2 children.
export interface ICategoryVo extends ICategory {
  children?: ICategoryVo[];
}

// The /category/l1 option shape: { value: id, label: name }.
export interface ICategoryL1Option {
  value: number;
  label: string;
}
