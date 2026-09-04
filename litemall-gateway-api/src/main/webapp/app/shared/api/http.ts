import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult } from 'app/config/types';
import { describeError } from 'app/i18n/errors';
import { t } from 'app/i18n';

/**
 * Customer-SPA api seam. Every `/srv` request the SPA makes lives in one of the
 * sibling `*Api.ts` modules and goes through these helpers — there is NO `/wx`
 * usage anywhere (the legacy wx-api is deleted; every consumed endpoint is
 * live on `/srv` — history in docs/SRV-FOLLOWUPS.md).
 *
 * baseAxios (config/axiosinstance.ts) injects the customer JWT as
 * `Authorization: Bearer` from sessionStorage, which the gateway relays
 * downstream. `unwrap` centralises the {errno,errmsg,data} envelope handling so
 * the slices/components stay terse; `toReject` normalises any thrown error into
 * the ApiResult<null> shape thunks pass to rejectWithValue.
 */
export const SRV = BASE_URL_CONTEXT; // '/srv'

export class ApiError extends Error {
  errno: number;
  constructor(errno: number, errmsg: string) {
    super(errmsg);
    this.name = 'ApiError';
    this.errno = errno;
  }
}

/**
 * Unwrap a `{errno,errmsg,data}` envelope, throwing `ApiError` on a non-zero
 * errno. Raw (non-enveloped) payloads — e.g. `/srv/suggest` returns a bare
 * array — are returned verbatim.
 */
export async function unwrap<T>(p: Promise<{ data: ApiResult<T> | T }>): Promise<T> {
  const res = await p;
  const body = res.data as ApiResult<T> | T;
  if (body && typeof body === 'object' && 'errno' in (body as ApiResult<T>)) {
    const env = body as ApiResult<T>;
    // Localised by errno when we know the code; the server's text verbatim otherwise.
    if (env.errno !== 0) throw new ApiError(env.errno, describeError(env.errno, env.errmsg));
    return env.data;
  }
  return body as T;
}

/** Normalise any thrown error into the ApiResult<null> shape for rejectWithValue. */
export function toReject(e: unknown): ApiResult<null> {
  if (e instanceof ApiError) return { errno: e.errno, errmsg: e.message, data: null };
  const msg = (e as { message?: string })?.message ?? t('errors:requestFailed');
  return { errno: 500, errmsg: msg, data: null };
}

export { baseAxios };
