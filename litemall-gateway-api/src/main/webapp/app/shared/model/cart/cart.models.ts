export interface IItemCart {
  id?: number;
  userId?: number;
  goodsId?: string;
  goodsSn?: string;
  goodsName?: string;
  productId?: number;
  price?: number;
  number?: number;
  specifications?: string[];
  checked?: boolean;
  picUrl?: string;
  // CJ Dropshipping lines: `source` marks the line as cj_dropshipping and `vid`
  // carries the CJ variant id as a RAW STRING (the vid exceeds JS safe-integer
  // range, so it must never be Number()-coerced). Both undefined for local goods.
  source?: string;
  vid?: string;
  addTime?: Date;
  updateTime?: Date;
  deleted?: boolean;
}

export interface ICartTotalData {
  goodsCount?: number;
  checkedGoodsCount?: number;
  goodsAmount?: number;
  checkedGoodsAmount?: number;
}
