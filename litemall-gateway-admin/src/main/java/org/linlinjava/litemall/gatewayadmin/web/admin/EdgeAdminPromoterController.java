package org.linlinjava.litemall.gatewayadmin.web.admin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.linlinjava.litemall.gatewayadmin.auth.AffiliateRefreshTokenService;
import org.linlinjava.litemall.gatewayadmin.auth.AffiliateUserStore;
import org.linlinjava.litemall.gatewayadmin.auth.AffiliateUserStore.AffiliateUser;
import org.linlinjava.litemall.gatewayadmin.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

import static org.linlinjava.litemall.gatewayadmin.web.admin.AdminEdge.blocking;

/**
 * Promoter (affiliate) management — Wave 5. Admin grants/revokes the
 * {@code is_promoter} flag on {@code litemall_user} rows (promoters are
 * ADMIN-GRANTED only, per the locked design) and inspects a per-affiliate
 * brokerage ledger. See {@link AdminEdge}.
 *
 * <p>Backed by {@link AffiliateUserStore} (edge-local JDBC) rather than
 * LitemallUserService because the shared LitemallUser domain does not yet map
 * {@code is_promoter}/{@code spread_*} — that litemall-db hand-edit belongs to
 * the order worktree this wave; swap to the service once it lands. Demoting a
 * promoter also revokes their affiliate refresh tokens, so live portal
 * sessions die at the next rotation.
 */
@RestController
@RequestMapping("/srv/private/admin/promoter")
public class EdgeAdminPromoterController {

    private final AffiliateUserStore users;
    private final AffiliateRefreshTokenService affiliateRefreshTokens;

    public EdgeAdminPromoterController(AffiliateUserStore users,
                                       AffiliateRefreshTokenService affiliateRefreshTokens) {
        this.users = users;
        this.affiliateRefreshTokens = affiliateRefreshTokens;
    }

    /** Paged live-user search. q matches username/nickname/mobile; promotersOnly narrows to affiliates. */
    @GetMapping("/list")
    public Mono<ApiResponse<?>> list(@RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "false") Boolean promotersOnly,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer limit) {
        return blocking(() -> {
            int safeLimit = Math.min(Math.max(limit, 1), 100);
            int safePage = Math.max(page, 1);
            boolean only = Boolean.TRUE.equals(promotersOnly);
            List<Map<String, Object>> rows = users.search(q, only, safePage, safeLimit).stream()
                    .map(EdgeAdminPromoterController::toRow)
                    .collect(Collectors.toList());
            return ApiResponse.ok(paged(rows, users.count(q, only), safePage, safeLimit));
        });
    }

    /** Toggle body: {userId, promoter}. Demote also revokes affiliate sessions. */
    @PostMapping("/toggle")
    public Mono<ApiResponse<?>> toggle(@RequestBody Map<String, Object> body) {
        return blocking(() -> {
            Integer userId = intOf(body.get("userId"));
            Boolean promoter = boolOf(body.get("promoter"));
            if (userId == null || promoter == null) {
                return AdminEdge.badArgument();
            }
            if (users.setPromoter(userId, promoter) == 0) {
                return AdminEdge.badArgumentValue();
            }
            if (!promoter) {
                affiliateRefreshTokens.revokeAllForUser(userId);
            }
            AffiliateUser updated = users.findLiveById(userId);
            return ApiResponse.ok(updated == null ? null : toRow(updated));
        });
    }

    /** Read-only per-affiliate brokerage ledger (V7 table, written by order's engine). */
    @GetMapping("/ledger")
    public Mono<ApiResponse<?>> ledger(@RequestParam Integer userId,
                                       @RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "10") Integer limit) {
        return blocking(() -> {
            int safeLimit = Math.min(Math.max(limit, 1), 100);
            int safePage = Math.max(page, 1);
            AffiliateUser user = users.findLiveById(userId);
            if (user == null) {
                return AdminEdge.badArgumentValue();
            }
            Map<String, Object> data =
                    paged(users.ledger(userId, safePage, safeLimit),
                            users.ledgerCount(userId), safePage, safeLimit);
            data.put("user", toRow(user));
            return ApiResponse.ok(data);
        });
    }

    /** Password (even hashed) never leaves the edge. */
    private static Map<String, Object> toRow(AffiliateUser u) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", u.getId());
        row.put("username", u.getUsername());
        row.put("nickname", u.getNickname());
        row.put("mobile", u.getMobile());
        row.put("avatar", u.getAvatar());
        row.put("isPromoter", u.isPromoter());
        row.put("spreadUid", u.getSpreadUid());
        row.put("spreadCount", u.getSpreadCount());
        row.put("payCount", u.getPayCount());
        row.put("brokeragePrice", u.getBrokeragePrice());
        row.put("addTime", u.getAddTime());
        return row;
    }

    /** Same envelope as AdminEdge.okList but for manually-paged JDBC results. */
    private static Map<String, Object> paged(List<?> list, long total, int page, int limit) {
        Map<String, Object> body = new HashMap<>(5);
        body.put("list", list);
        body.put("total", total);
        body.put("pages", (int) Math.ceil(total / (double) limit));
        body.put("page", page);
        body.put("limit", limit);
        return body;
    }

    private static Integer intOf(Object v) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return v == null ? null : Integer.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean boolOf(Object v) {
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if ("true".equals(String.valueOf(v))) {
            return Boolean.TRUE;
        }
        if ("false".equals(String.valueOf(v))) {
            return Boolean.FALSE;
        }
        return null;
    }
}
