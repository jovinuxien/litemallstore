import axios from 'axios';

/**
 * Single axios instance shared by every customer-SPA redux slice. Centralises:
 *  - the same-origin base URL ('' — paths are prefixed with BASE_URL_CONTEXT
 *    '/srv' per call and proxied to the gateway by the dev-server),
 *  - Bearer-token injection from the customer JWT in sessionStorage.
 *
 * It deliberately does NOT unwrap the {errno,errmsg,data} envelope: the slices
 * own that (errno !== 0 -> rejectWithValue, else return response.data.data), so
 * every thunk reads identically. Keep that contract when adding new slices —
 * import { baseAxios } here instead of the bare `axios` default.
 */
export const baseAxios = axios.create({
  baseURL: '',
  timeout: 5000,
});

baseAxios.interceptors.request.use(config => {
  const token = sessionStorage.getItem('customerToken');
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Expired/invalid JWT: drop the stored session and send the customer to
// /login via a hard redirect — the full reload resets redux, which keeps this
// module free of a circular import on the store. Auth failures surface two
// ways depending on the service: HTTP 401, or the litemall envelope
// {errno:501,"please login"} inside an HTTP 200. (Today the order service
// answers a bad token with the generic errno 502 — follow-up filed for the
// order worktree to emit 401/501 so this hook can fire.) The rejection still
// propagates so callers' error paths run.
function expireSession(): void {
  sessionStorage.removeItem('customerToken');
  sessionStorage.removeItem('customerRefreshToken');
  sessionStorage.removeItem('customerUserInfo');
  if (window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
}

baseAxios.interceptors.response.use(
  response => {
    if (response.data?.errno === 501 && sessionStorage.getItem('customerToken')) {
      expireSession();
    }
    return response;
  },
  error => {
    if (error?.response?.status === 401) {
      expireSession();
    }
    return Promise.reject(error);
  }
);

export default baseAxios;
