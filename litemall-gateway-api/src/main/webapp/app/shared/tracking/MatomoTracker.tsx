import React, { useEffect, useSyncExternalStore } from 'react';
import { useLocation } from 'react-router-dom';

import { loadSiteConfig } from 'app/shared/config/siteConfig';
import { consentSnapshot, subscribeConsent } from 'app/shared/tracking/consent';
import { applyConsent, initMatomo, trackPageView } from 'app/shared/tracking/matomo';
import { goodsIdFromRoute } from 'app/shared/util/slug';

/**
 * Route-change page-view tracking (Wave-6 Task A). Mounted once inside
 * BrowserRouter (next to InviteCapture). On mount it fetches
 * `/auth/site-config` and hands the decision to `initMatomo` — unconfigured
 * or Do-Not-Track ⇒ everything below is a no-op.
 *
 * <p>Wave-7: also relays consent changes into the tracker. Nothing is tracked
 * until the visitor accepts (see matomo.ts); this component only reports views
 * and lets matomo.ts decide what to do with them.
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
  const { choice } = useSyncExternalStore(subscribeConsent, consentSnapshot);

  useEffect(() => {
    // Shared with Checkout's Stripe key — one /auth/site-config fetch, not two.
    // loadSiteConfig never rejects: a failure resolves to "nothing configured".
    loadSiteConfig().then(initMatomo);
  }, []);

  // Accepting from the banner (or withdrawing on /cookies) takes effect immediately,
  // without a reload. initMatomo applies any stored choice itself, so this only
  // carries later changes.
  useEffect(() => {
    applyConsent(choice);
  }, [choice]);

  useEffect(() => {
    const { pathname, search } = location;
    const url = AUTH_PATHS.has(pathname)
      ? window.location.origin + pathname
      : window.location.origin + pathname + search;
    // Slugged PDP URLs (Wave-13) carry the goods id in the leading digits;
    // the Matomo dimension must keep receiving the bare id.
    const goodsId = goodsIdFromRoute(PRODUCT_PATH.exec(pathname)?.[1]);
    trackPageView(url, document.title, goodsId);
  }, [location]);

  return null;
};

export default MatomoTracker;
