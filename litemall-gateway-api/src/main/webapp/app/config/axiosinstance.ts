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

export default baseAxios;
