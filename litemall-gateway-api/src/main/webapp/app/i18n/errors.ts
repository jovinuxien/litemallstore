import { i18n, t } from './index';

/**
 * Server error messages, localised BY ERRNO on the client.
 *
 * Every backend (order, goods-management, promotion, the edge's /auth) answers
 * `{errno, errmsg}` with an English `errmsg`; there is no MessageSource anywhere in
 * litemall and asking four services to localise would be the wrong seam. The typed
 * NUMBER is the contract the repo pins — the message is not. So: when we have a
 * translation for the errno (errors.json → byCode), use it; otherwise render the server's
 * text verbatim, exactly as every acceptance test in this repo expects for typed refusals
 * we have not catalogued. Blank server text falls back to a generic localised line.
 */
export const describeError = (errno: number | string | null | undefined, errmsg?: string | null): string => {
  const code = String(errno ?? '').trim();
  if (code && i18n.exists(`errors:byCode.${code}`)) return t(`errors:byCode.${code}`);
  const verbatim = (errmsg ?? '').trim();
  return verbatim || t('errors:requestFailed');
};
