// Relative gateway prefix — no hardcoded host. The webpack dev server proxies
// '/srv' to the gateway (see webpack/webpack.dev.js); in prod the SPA is served
// by the same gateway origin, so a relative base works in both cases.
// The legacy hardcoded admin host + per-request admin header scheme are gone:
// admin calls now go through '/srv' as an authenticated admin
// (Bearer admin JWT, see shared/reducers/admin-auth).
export const BASE_URL_CONTEXT = '/srv';
