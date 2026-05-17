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
