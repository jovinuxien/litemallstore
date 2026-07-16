import React, { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

import { initMatomo, trackPageView } from 'app/shared/tracking/matomo';

/**
 * Route-change page-view tracking (Wave-6 Task A). Mounted once inside
 * BrowserRouter (next to InviteCapture). On mount it fetches
 * `/auth/site-config` and hands the decision to `initMatomo` — unconfigured
 * or Do-Not-Track ⇒ everything below is a no-op.
 *
 * - Every location change is one page view (views fired before the config
 *   fetch resolves are buffered by matomo.ts).
 * - Product detail (`/product/:id`) carries the route's goods id (native or
 *   `cj_<pid>` — the OCS document key) as a custom dimension; tracked HERE,
 *   not in Detail.tsx, so there is exactly one view per navigation.
 * - Auth pages (/login, /register, /reset) are tracked by PATH ONLY: no query
 *   string, so tokens/invite codes/prefill params never reach Matomo, and no
 *   form contents (Matomo tracks URLs, not inputs — we enable no form/content
 *   tracking features anywhere).
 */

const AUTH_PATHS = new Set(['/login', '/register', '/reset']);
const PRODUCT_PATH = /^\/product\/([^/]+)$/;

const MatomoTracker: React.FC = () => {
  const location = useLocation();

  useEffect(() => {
    fetch('/auth/site-config')
      .then(res => res.json())
      .then(env => initMatomo(env?.data ?? { matomoUrl: null, matomoSiteId: null }))
      .catch(() => initMatomo({ matomoUrl: null, matomoSiteId: null }));
  }, []);

  useEffect(() => {
    const { pathname, search } = location;
    const url = AUTH_PATHS.has(pathname)
      ? window.location.origin + pathname
      : window.location.origin + pathname + search;
    const goodsId = PRODUCT_PATH.exec(pathname)?.[1];
    trackPageView(url, document.title, goodsId);
  }, [location]);

  return null;
};

export default MatomoTracker;
