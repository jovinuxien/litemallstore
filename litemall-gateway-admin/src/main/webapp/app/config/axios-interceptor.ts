import axios from 'axios';

import { getAdminToken } from 'app/shared/reducers/admin-auth';

const TIMEOUT = 1 * 60 * 1000;
axios.defaults.timeout = TIMEOUT;
axios.defaults.baseURL = SERVER_API_URL;

const setupAxiosInterceptors = onUnauthenticated => {
  const onRequestSuccess = config => {
    // Attach the admin JWT as a Bearer token on every request that doesn't
    // already carry an Authorization header (the login/refresh calls don't).
    // The edge validates it for /srv/private/admin/** and /srv/order/admin/**
    // and relays a trusted identity downstream (MachineTokenRelayFilter).
    const token = getAdminToken();
    if (token && !config.headers?.Authorization) {
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  };
  const onResponseSuccess = response => response;
  const onResponseError = err => {
    const status = err.status || (err.response ? err.response.status : 0);
    if (status === 403 || status === 401) {
      onUnauthenticated();
    }
    return Promise.reject(err);
  };
  axios.interceptors.request.use(onRequestSuccess);
  axios.interceptors.response.use(onResponseSuccess, onResponseError);
};

export default setupAxiosInterceptors;
