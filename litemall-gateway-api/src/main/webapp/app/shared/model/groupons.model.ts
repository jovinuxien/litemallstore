export interface IGroupons {
  id: number;
  goodsId: number;
  goodsName: 'string';
  picUrl: string;
  discount: number;
  discountMember: number;
  expireTime: Date;
  status: number;
  addTime: Date;
  updateTime: Date;
  deleted: boolean;
}
