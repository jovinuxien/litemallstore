export interface ApiResult<T> {
  errno: number;
  data: T;
  errmsg: string;
}

export interface BaseState<T> {
  loading: 'idle' | 'pending' | 'succeeded' | 'failed';
  errorMessage: string | null;
  errorNumber: number | null;
  data: T;
}
