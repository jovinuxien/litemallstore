import { IGood } from './product/product.model';

export interface ICoupon {
  id?: number;
  name?: string;
  desc?: string;
  tag?: string;
  discount?: number;
  min?: number;
  days?: number;
}

export interface IChannel {
  id?: number;
  name?: string;
  picUrl?: string;
}

export interface IBanner {
  id?: number;
  name?: string;
  link?: string;
  url?: string;
  position?: number;
  content?: string;
  enabled?: boolean;
  addTime?: Date;
  updateTime?: Date;
  deleted?: boolean;
  index?: number;
}
export interface ITopic {
  id?: number;
  title?: string;
  subtitle?: string;
  price?: number;
  readCount: number;
  picUrl?: string;
}

export interface IFloorGood {
  nameCategory?: string;
  goodsList?: IGood[];
  id?: number;
}
export type HomeData = {
  newGoodsList: IGood[];
  couponList: ICoupon[];
  channel: IChannel[];
  grouponList: [];
  banner: IBanner[];
  brandList: [];
  hotGoodsList: IGood[];
  topicList: [];
  floorGoodsList: IFloorGood[];
};

export const defaultHomeValue: Readonly<HomeData> = {
  newGoodsList: [],
  couponList: [],
  channel: [],
  grouponList: [],
  banner: [],
  brandList: [],
  hotGoodsList: [],
  topicList: [],
  floorGoodsList: [],
};
