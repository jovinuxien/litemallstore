import axios, { AxiosInstance } from 'axios';

/** litemall response envelope. */
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

export interface ApiClientOptions {
  /**
   * Base URL. Default '' = same-origin: each SPA is served by its own edge
   * gateway and calls relative /auth, /srv, /wx (webpack dev proxy in dev).
   */
  baseURL?: string;
  /** sessionStorage key holding the edge JWT (e.g. 'customerToken' / 'adminToken'). */
  tokenKey: string;
  timeout?: number;
}

/**
 * Bearer-token axios client. Replaces the legacy X-Litemall-Token scheme:
 * the edge gateways (Phase 2 / 3c) issue/validate a self-signed JWT and
 * expect `Authorization: Bearer`. The response interceptor unwraps the
 * {errno,errmsg,data} envelope (errno 0 -> data, else reject errmsg).
 */
export function createApiClient(options: ApiClientOptions): AxiosInstance {
  const instance = axios.create({
    baseURL: options.baseURL ?? '',
    timeout: options.timeout ?? 5000,
  });

  instance.interceptors.request.use(config => {
    const token = sessionStorage.getItem(options.tokenKey);
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  instance.interceptors.response.use(
    response => {
      const res = response.data;
      if (res && typeof res.errno !== 'undefined') {
        if (res.errno === 0) {
          return res.data;
        }
        return Promise.reject(res.errmsg);
      }
      return response;
    },
    error => Promise.reject(error)
  );

  return instance;
}
