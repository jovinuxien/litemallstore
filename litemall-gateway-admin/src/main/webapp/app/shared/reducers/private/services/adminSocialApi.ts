import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';
import { fromServerDateTime } from 'app/shared/util/server-datetime';

// RTK Query client for the Wave-6 social-posting admin surface, served by
// promotion-service under /srv/private/admin/social (routed there by the
// promotion-service gateway route, machine-token relay + X-User-* downstream).
//
// Contract reconciled against promotion's LitemallSocialAdminController /
// SocialComposePreviewDtoResponse (WIP at build time; final check against the
// committed docs/handoff-social-composer.md):
// - GET  /compose-preview?goodsId= → {goodsId, goodsName, price, dealPrice?,
//   dealId?, caption, images[], videoUrl?, platforms[{platform, displayName,
//   enabled, available, reason?, shareUrl}]} — the UTM share URL is
//   PER-PLATFORM (utm_source differs); 404 on unknown goods.
// - POST /post {goodsId, caption, mediaUrl?, platforms[]} → 200 {results:
//   [{platform, postId, status posted|failed, externalPostId?, error?}]} —
//   fail-soft: disabled adapters still 2xx with failed rows; 400 {error} on
//   caller mistakes.
// - GET  /list?page=&limit=[&status=&platform=] → {total, page, limit,
//   list[]} (no `pages` — pager uses total/heuristic).
// - POST /{id}/retry → 200 result DTO; 400 {error} on non-failed row; 404.
// The normalisers stay tolerant of shape drift (bare arrays, LocalDateTime
// arrays vs ISO strings).

export type SocialPlatform = 'meta_fb' | 'meta_ig' | 'tiktok';
export type SocialPostStatus = 'draft' | 'posted' | 'failed';

export const SOCIAL_PLATFORMS: SocialPlatform[] = ['meta_fb', 'meta_ig', 'tiktok'];
export const PLATFORM_LABEL: Record<SocialPlatform, string> = {
  meta_fb: 'Facebook',
  meta_ig: 'Instagram',
  tiktok: 'TikTok',
};

export interface IPlatformAvailability {
  platform: SocialPlatform;
  /** Adapter flag on the service (litemall.promotion.social.*.enabled). */
  enabled?: boolean;
  /** Whether this goods can be posted there (TikTok requires a video). */
  available?: boolean;
  /** Human-readable reason when unavailable/disabled. */
  reason?: string;
  /** UTM-tagged share URL this platform's post will carry (utm_source differs per platform). */
  shareUrl?: string;
}

export interface IComposePreview {
  goodsId?: number;
  goodsName?: string;
  /** Current retail price (already the deal price while a deal is live). */
  price?: number;
  /** Live flash-deal price, null when no deal is live. */
  dealPrice?: number;
  dealId?: number;
  caption?: string;
  images: string[];
  videoUrl?: string;
  platforms: IPlatformAvailability[];
}

export interface ISocialPost {
  id?: number;
  goodsId?: number;
  platform?: SocialPlatform;
  caption?: string;
  mediaUrl?: string;
  linkUrl?: string;
  status?: SocialPostStatus;
  externalPostId?: string;
  error?: string;
  /** Admin id/name, or 'auto' for the deal auto-poster. */
  postedBy?: string;
  /** Flash-deal id for auto-posted rows. */
  dealId?: number;
  addTime?: string;
  updateTime?: string;
}

export interface IPlatformPostResult {
  platform?: SocialPlatform;
  success?: boolean;
  status?: SocialPostStatus;
  error?: string;
  postId?: number;
  externalPostId?: string;
}

export interface SocialListParams {
  page: number;
  limit: number;
  status?: SocialPostStatus | '';
  platform?: SocialPlatform | '';
}

export interface SocialPostCommand {
  goodsId: number;
  caption: string;
  mediaUrl?: string;
  platforms: SocialPlatform[];
}

// Paged list with optional metadata: the pager falls back to the
// rowCount < limit heuristic when total/pages are absent (bare array).
export interface SocialPage {
  list: ISocialPost[];
  total?: number;
  pages?: number;
}

const clean = (params: Record<string, unknown>): Record<string, unknown> => {
  const out: Record<string, unknown> = {};
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v;
  });
  return out;
};

type Raw = Record<string, unknown>;
const str = (v: unknown): string | undefined => (typeof v === 'string' && v ? v : undefined);
const num = (v: unknown): number | undefined => (typeof v === 'number' ? v : undefined);

const toPost = (r: Raw): ISocialPost => ({
  id: num(r.id) ?? num(r.postId) ?? num(r.socialPostId),
  goodsId: num(r.goodsId),
  platform: str(r.platform) as SocialPlatform | undefined,
  caption: str(r.caption),
  mediaUrl: str(r.mediaUrl),
  linkUrl: str(r.linkUrl),
  status: str(r.status)?.toLowerCase() as SocialPostStatus | undefined,
  externalPostId: str(r.externalPostId),
  error: str(r.error) ?? str(r.errorMessage),
  postedBy: str(r.postedBy),
  dealId: num(r.dealId),
  addTime: fromServerDateTime(r.addTime),
  updateTime: fromServerDateTime(r.updateTime),
});

// Accept `platforms` as an array of availability rows or a keyed object map.
const toAvailability = (raw: unknown): IPlatformAvailability[] => {
  const rows: IPlatformAvailability[] = [];
  const push = (platform: string, r: Raw) =>
    rows.push({
      platform: platform as SocialPlatform,
      enabled: typeof r.enabled === 'boolean' ? r.enabled : undefined,
      available: typeof r.available === 'boolean' ? r.available : undefined,
      reason: str(r.reason) ?? str(r.message),
      shareUrl: str(r.shareUrl),
    });
  if (Array.isArray(raw)) {
    raw.forEach(r => {
      const p = str((r as Raw).platform);
      if (p) push(p, r as Raw);
    });
  } else if (raw && typeof raw === 'object') {
    Object.entries(raw as Record<string, Raw>).forEach(([p, r]) => push(p, r ?? {}));
  }
  // Guarantee all three platforms appear so the composer can always render
  // its checkboxes; unknown platforms default to unavailable.
  SOCIAL_PLATFORMS.forEach(p => {
    if (!rows.some(r => r.platform === p)) rows.push({ platform: p, available: false, reason: 'Not reported by the service.' });
  });
  return rows.filter(r => SOCIAL_PLATFORMS.includes(r.platform));
};

const toPreview = (r: Raw): IComposePreview => {
  const images = (Array.isArray(r.images) && r.images) || (Array.isArray(r.candidateImages) && r.candidateImages) || [];
  return {
    goodsId: num(r.goodsId),
    goodsName: str(r.goodsName) ?? str(r.name),
    price: num(r.price),
    dealPrice: num(r.dealPrice),
    dealId: num(r.dealId),
    caption: str(r.caption),
    images: (images as unknown[]).filter((u): u is string => typeof u === 'string'),
    videoUrl: str(r.videoUrl),
    platforms: toAvailability(r.platforms ?? r.availability),
  };
};

const toResult = (r: Raw): IPlatformPostResult => {
  const status = str(r.status)?.toLowerCase() as SocialPostStatus | undefined;
  return {
    platform: str(r.platform) as SocialPlatform | undefined,
    success: typeof r.success === 'boolean' ? r.success : status ? status === 'posted' : undefined,
    status,
    error: str(r.error) ?? str(r.errorMessage) ?? str(r.message),
    postId: num(r.id) ?? num(r.postId),
    externalPostId: str(r.externalPostId),
  };
};

// The post envelope carries one result per requested platform; find the array
// wherever the envelope puts it ({results}, {data}, or a bare array).
const toResults = (r: unknown): IPlatformPostResult[] => {
  if (Array.isArray(r)) return r.map(x => toResult(x as Raw));
  const env = (r ?? {}) as Raw;
  const arr = [env.results, env.data, (env.data as Raw | undefined)?.results].find(Array.isArray);
  return arr ? (arr as Raw[]).map(toResult) : [];
};

const toPage = (r: unknown): SocialPage => {
  if (Array.isArray(r)) return { list: r.map(x => toPost(x as Raw)) };
  const env = (r ?? {}) as Raw;
  const body = (Array.isArray(env.list) || env.total != null ? env : (env.data as Raw)) ?? {};
  const list = Array.isArray(body.list) ? body.list : Array.isArray(body) ? (body as unknown as Raw[]) : [];
  return { list: (list as Raw[]).map(toPost), total: num(body.total), pages: num(body.pages) };
};

export const adminSocialApi = createApi({
  reducerPath: 'adminSocialApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin/social',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  tagTypes: ['SocialPost'],
  endpoints: builder => ({
    composePreview: builder.query<IComposePreview, number | string>({
      query: goodsId => ({ url: '/compose-preview', params: { goodsId } }),
      transformResponse: (r: Raw) => toPreview(r ?? {}),
    }),
    postSocial: builder.mutation<IPlatformPostResult[], SocialPostCommand>({
      query: body => ({ url: '/post', method: 'POST', body }),
      transformResponse: toResults,
      invalidatesTags: ['SocialPost'],
    }),
    listSocialPosts: builder.query<SocialPage, SocialListParams>({
      query: ({ page, limit, status, platform }) => ({ url: '/list', params: clean({ page, limit, status, platform }) }),
      transformResponse: toPage,
      providesTags: ['SocialPost'],
    }),
    retrySocialPost: builder.mutation<unknown, number>({
      query: id => ({ url: `/${id}/retry`, method: 'POST' }),
      invalidatesTags: ['SocialPost'],
    }),
  }),
});

// Normalise a mutation result into an error message (null on success). The
// social controller's 4xx bodies carry {error: "..."}; a 2xx retry answer is
// a result DTO (no success flag) — treat any 2xx as success.
export const socialOpMessage = (res: unknown): string | null => {
  const r = res as {
    data?: { success?: boolean; message?: string };
    error?: { status?: number | string; data?: { error?: string; message?: string } };
  };
  if (r && 'error' in r && r.error) {
    return r.error.data?.error || r.error.data?.message || `Request failed (${r.error.status ?? 'network'})`;
  }
  if (r && 'data' in r && r.data && typeof r.data.success === 'boolean') {
    return r.data.success ? null : r.data.message || 'Request failed.';
  }
  if (r && 'data' in r) return null;
  return 'Request failed.';
};

export const { useComposePreviewQuery, usePostSocialMutation, useListSocialPostsQuery, useRetrySocialPostMutation } = adminSocialApi;
