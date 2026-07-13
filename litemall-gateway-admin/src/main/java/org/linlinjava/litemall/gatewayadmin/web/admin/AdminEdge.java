package org.linlinjava.litemall.gatewayadmin.web.admin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.linlinjava.litemall.gatewayadmin.web.ApiResponse;
import org.springframework.security.core.Authentication;

import com.github.pagehelper.Page;

/**
 * Shared plumbing for the edge-hosted admin CRUD controllers
 * ({@code /srv/private/admin/{ad,admin,notice,log,role}}).
 *
 * <p>These controllers exist at the edge because the DDD services do not (yet)
 * own these verticals: the only other implementation is the legacy
 * {@code litemall-admin-api}, which is deliberately not routed (ROUTING.md).
 * They are thin ports of the legacy controllers over the same litemall-db
 * services, kept under the {@code /srv/private/admin} prefix so each feature
 * can migrate into a real service later with a route change only. The former
 * coupon/groupon edge controllers were deleted 2026-07-10 when
 * promotion-service (owning {@code /srv/private/admin/promotion/**}) landed
 * on master and the SPA was re-pointed at it; ads remain edge-hosted until a
 * service claims them.
 *
 * <p>Blocking MyBatis — every handler wraps its work in
 * {@code Mono.fromCallable(...).subscribeOn(boundedElastic)}, same as
 * {@code AuthController}.
 */
final class AdminEdge {

    /** litemall error codes kept compatible with core ResponseUtil / AdminResponseCode. */
    static final int BAD_ARGUMENT = 401;
    static final int BAD_ARGUMENT_VALUE = 402;
    static final int UPDATE_FAILED = 505;
    static final int ADMIN_INVALID_NAME = 601;
    static final int ADMIN_INVALID_PASSWORD = 602;
    static final int ADMIN_NAME_EXIST = 602;
    static final int ADMIN_DELETE_NOT_ALLOWED = 604;
    /** Legacy AdminResponseCode.ADMIN_INVALID_ACCOUNT — wrong old password at /profile/password. */
    static final int ADMIN_INVALID_ACCOUNT = 605;
    static final int ROLE_NAME_EXIST = 640;
    static final int ROLE_USER_EXIST = 641;
    static final int NOTICE_UPDATE_NOT_ALLOWED = 660;

    private AdminEdge() {
    }

    static ApiResponse<Object> badArgument() {
        return ApiResponse.fail(BAD_ARGUMENT, "invalid argument");
    }

    static ApiResponse<Object> badArgumentValue() {
        return ApiResponse.fail(BAD_ARGUMENT_VALUE, "invalid argument value");
    }

    static ApiResponse<Object> updateFailed() {
        return ApiResponse.fail(UPDATE_FAILED, "update failed");
    }

    /**
     * The litemall paginated-list envelope ({@code {list,total,page,limit,pages}}),
     * mirroring core ResponseUtil.okList: pagination metadata comes from the
     * PageHelper {@link Page} the db services return.
     */
    static ApiResponse<Map<String, Object>> okList(List<?> list) {
        return okList(list, list);
    }

    /** Variant where {@code data} was re-mapped but {@code pageSource} carries the page meta. */
    static ApiResponse<Map<String, Object>> okList(List<?> data, List<?> pageSource) {
        Map<String, Object> body = new HashMap<>(5);
        body.put("list", data);
        if (pageSource instanceof Page) {
            Page<?> page = (Page<?>) pageSource;
            body.put("total", page.getTotal());
            body.put("pages", page.getPages());
            body.put("page", page.getPageNum());
            body.put("limit", page.getPageSize());
        } else {
            body.put("total", (long) data.size());
            body.put("pages", 1);
            body.put("page", 1);
            body.put("limit", data.size());
        }
        return ApiResponse.ok(body);
    }

    /**
     * The db services concatenate sort/order into SQL, so both are whitelisted
     * here (the legacy edge relied on litemall-core's @Sort/@Order validators,
     * which are unavailable in this reactive module).
     */
    static String sort(String sort, String fallback) {
        return sort != null && sort.matches("[a-zA-Z0-9_]+") ? sort : fallback;
    }

    static String order(String order) {
        return "asc".equalsIgnoreCase(order) || "desc".equalsIgnoreCase(order) ? order : "desc";
    }

    /** Runs blocking MyBatis work off the Netty event loop. */
    static reactor.core.publisher.Mono<ApiResponse<?>> blocking(
            java.util.concurrent.Callable<ApiResponse<?>> work) {
        return reactor.core.publisher.Mono.fromCallable(work)
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /** The admin JWT subject is the litemall_admin id (see AuthController). */
    static Integer adminId(Authentication authentication) {
        try {
            return Integer.valueOf(String.valueOf(authentication.getPrincipal()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
