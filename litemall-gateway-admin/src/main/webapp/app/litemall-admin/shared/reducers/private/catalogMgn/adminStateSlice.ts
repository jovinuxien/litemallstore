import { ApiResult } from 'app/config/types';

interface RowUser {
  day: Date;
  users: number;
}

interface RowOrder {
  day: Date;
  orders: number;
  customers: number;
  amount: number;
  pcr: number;
}

interface RowGoods {
  day: Date;
  orders: number;
  products: number;
  amount: number;
}

interface AdminApiStateResult
  extends ApiResult<{
    columns: string[];
    rows: [];
  }> {}
