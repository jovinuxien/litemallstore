import { baseAxios, SRV, unwrap } from './http';

/**
 * Canonical promotion surface (/srv/promotion/**) — the DDD controllers on
 * the promotion service. The coupon/groupon LEGACY paths the older views use
 * live in userApi/contentApi; this module carries the interactive group-buy
 * (combination) flow, which only exists canonically. Contract:
 * litemall-promotion-service/docs/spec-groupon-priced-submit-contract.md.
 *
 * These endpoints return bare DTOs (no errno envelope): reads are plain
 * JSON, mutations return {success, operationType, message, data} with
 * HTTP 200 on success and 400 on a domain failure (axios throws on 400 —
 * read error.response.data.message).
 */

/** A group-buy campaign (CombinationDtoResponse). */
export interface ICombination {
  combinationId?: number;
  goodsId?: number;
  title?: string;
  picUrl?: string;
  combinationPrice?: number;
  originalPrice?: number;
  requiredMembers?: number;
  status?: string;
  startTime?: string | number[];
  endTime?: string | number[];
}

/** One participant slot ("pink") in a group (CombinationPinkDtoResponse). */
export interface ICombinationPink {
  pinkId?: number;
  combinationId?: number;
  headId?: number;
  userId?: number;
  orderId?: number | null;
  requiredMembers?: number;
  memberCount?: number;
  expireTime?: string | number[];
  status?: string;
  members?: ICombinationPink[];
}

export interface IPromotionOperation {
  success?: boolean;
  message?: string;
  operationType?: string;
  data?: Record<string, unknown>;
}

export const promotionApi = {
  combinationActive: () => unwrap<ICombination[]>(baseAxios.get(`${SRV}/promotion/combination/active`)),
  combinationDetail: (combinationId: number) =>
    unwrap<ICombination>(baseAxios.get(`${SRV}/promotion/combination/${combinationId}`)),
  /** Start a new group as leader → data {pinkId, combinationId, requiredMembers, expireTime}. */
  combinationStart: (combinationId: number) =>
    unwrap<IPromotionOperation>(baseAxios.post(`${SRV}/promotion/combination/${combinationId}/start`)),
  /** Join an open group → data {pinkId, groupPinkId, memberCount, requiredMembers, completed}. */
  combinationJoin: (leaderPinkId: number) =>
    unwrap<IPromotionOperation>(baseAxios.post(`${SRV}/promotion/combination/pink/${leaderPinkId}/join`)),
  /** The caller's own slots (leader or member), newest first. */
  combinationMy: () => unwrap<ICombinationPink[]>(baseAxios.get(`${SRV}/promotion/combination/my`)),
  combinationPink: (pinkId: number) =>
    unwrap<ICombinationPink>(baseAxios.get(`${SRV}/promotion/combination/pink/${pinkId}`)),
};
