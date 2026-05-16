import { ProductStatus } from '../enumerations/product-status.model';
import { Size } from '../enumerations/size.model';

export interface ReducedGood {
  id: number;
  name: string;
  brief: string;
  picUrl: string;
  isNew: boolean;
  isHot: boolean;
  counterPrice: number;
  retailPrice: number;
}

export interface IGood {
  id?: number;
  sku?: string;
  name?: string;
  link?: string;
  brief?: string;
  description?: string | null;
  picUrl?: string;

  price?: number | null;
  counterPrice?: number | null;
  retailPrice?: number | null;
  originPrice?: number | null;
  discountPrice?: number | null;
  taxable?: boolean | null;

  isFreeShipping?: boolean | null;
  isNew?: boolean | null;
  isHot?: boolean | null;
  star?: number | null;
  itemSize?: Size | null;
  salesQuantity?: number | null;
  status?: ProductStatus | null;
  length?: number | null;
}

export const defaultValue: Readonly<IGood> = {
  taxable: false,
};

export interface ValueList {
  id: number;
  goodsId: number;
  specification: string;
  value: string;
  picUrl: string;
  addTime: Date;
  updateTime: Date;
  deleted: boolean;
}

export interface SpecificationList {
  name: string;
  valueList: ValueList[];
}
export interface Issue {
  id: number;
  question: string;
  answer: string;
  addTime: Date;
  updateTime: Date;
  deleted: boolean;
}

export interface commentData {
  addTime: Date;
  picList: string[]; //['https://yanxuan.nosdn.127.net/b007ab3de1c4c9fbea625db5615d49aa.jpg'];
  adminContent: string;
  nickname: string; //'user123'
  id: number;
  avatar: string;
  content: string;
}
export interface Comments {
  data: commentData[];
  count: number;
}

export interface Attribute {}

export interface ProductList {
  id: number;
  goodsId: number;
  specifications: Specification[]; //example: ['standard', 'large']
  price: number;
  number: number;
  url: string;
  addTime: Date;
  updateTime: Date;
  deleted: boolean;
}

export interface Specification {
  typeProduct: string;
}

export interface Info {
  id: number;
  goodsSn: number;
  name: string;
  categoryId: number;
  brandId: number;
  gallery: string[];
  keywords: '';
  brief: string; //'Crispy and milky, sweet and sour aftertaste';
  isOnSale: boolean;
  sortOrder: number;
  picUrl: string; //'http://yanxuan.nosdn.127.net/767b370d07f3973500db54900bcbd2a7.png';
  shareUrl: string;
  isNew: boolean;
  isHot: boolean;
  unit: string;
  counterPrice: number;
  retailPrice: number;
  addTime: Date;
  updateTime: Date;
  deleted: boolean;
  detail: string;
}

export interface IGoodsDetail {
  id: number;
  goodsSn: number;
  name: string;
  categoryId: number;
  brandId: number;
  gallery: string[];
  keywords: '';
  brief: string; //'Crispy and milky, sweet and sour aftertaste';
  isOnSale: boolean;
  sortOrder: number;
  picUrl: string; //'http://yanxuan.nosdn.127.net/767b370d07f3973500db54900bcbd2a7.png';
  shareUrl: string;
  isNew: boolean;
  isHot: boolean;
  unit: string;
  counterPrice: number;
  retailPrice: number;
  addTime: Date;
  updateTime: Date;
  deleted: boolean;
  detail: string;
}

export interface CardFeaturedProductData {
  name: string;
  link: string;
  star: number;
  price: number;
  retailPrice: number;
}

export interface IRelatedGood {
  total: number;
  pages: number;
  limit: number;
  page: number;
  list: IGood[];
}

export interface IIssue {
  id?: number;
  question: string;
  answer: string;
  addTime?: Date;
  deleted?: boolean;
  updateTime?: Date;
}

export interface IAttribute {
  addTime: Date;
  attribute: string;
  deleted: false;
  goodsId: number;
  id: number;
  updateTime: Date;
  value: string;
}
