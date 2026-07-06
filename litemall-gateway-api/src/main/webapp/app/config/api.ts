/**
 * Customer SPA → edge contract. Same-origin BFF: the SPA is served by
 * litemall-gateway-api (:8090) in prod and proxied to it by the webpack
 * dev-server (:9000) in dev, so all paths are relative.
 *
 * `/srv` is the gateway prefix routed to the load-balanced DDD services
 * (see application.yml: /srv/** -> lb://litemall-goods-management, etc.).
 * Auth lives at /auth (AuthController) and wx at /wx (wx-api). This SPA is
 * customer-realm only: it carries no admin-edge base URL and calls no
 * admin-only paths — the admin realm is served by its own gateway SPA.
 */
export const BASE_URL_CONTEXT = '/srv';
