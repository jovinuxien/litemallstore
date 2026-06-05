/**
 * Shared API types live in @litemall/shared (the workspace package consumed by
 * both the customer and admin SPAs). This module re-exports them so the
 * customer slices can keep importing from 'app/config/types' without a second,
 * divergent copy of the {errno,errmsg,data} envelope / loading-state shapes.
 */
export type { ApiResult, BaseState } from '@litemall/shared';
