/**
 * Wave 26 — the storefront is HTTPS-only, so a plain-`http://` image is not a
 * picture, it is a browser-blocked hole in the page. The legacy seed rows still
 * in the database (brands and topics all point at `http://yanxuan.nosdn.127.net`)
 * are exactly that case.
 *
 * Returns the url when it can actually load, otherwise null so the caller can
 * render its placeholder instead of a broken image. Protocol-relative and
 * app-relative paths (`/_cdn/...`, the CJ edge rewrite) pass through.
 */
export const secureImageUrl = (url: string | null | undefined): string | null => {
  const trimmed = typeof url === 'string' ? url.trim() : '';
  if (!trimmed) return null;
  return /^http:\/\//i.test(trimmed) ? null : trimmed;
};

export default secureImageUrl;
