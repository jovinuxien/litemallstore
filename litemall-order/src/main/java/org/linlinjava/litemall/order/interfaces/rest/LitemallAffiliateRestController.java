package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.domain.LitemallUserBrokerageRecord;
import org.linlinjava.litemall.db.domain.LitemallUserExtract;
import org.linlinjava.litemall.order.application.internal.LitemallAffiliateServiceLayer;
import org.linlinjava.litemall.order.application.internal.LitemallExtractServiceLayer;
import org.linlinjava.litemall.order.application.util.exception.wallet.LitemallBrokerageBelowMinimumException;
import org.linlinjava.litemall.order.application.util.exception.wallet.LitemallBrokerageInsufficientException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallExtractAggregate;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallExtractRequestCommand;
import org.linlinjava.litemall.order.interfaces.util.AffiliateErrno;
import org.linlinjava.litemall.order.interfaces.util.InviteCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Affiliate self-service portal under {@code /srv/private/affiliate/**} (Wave 5).
 *
 * <p>Trust model: the admin-edge gateway authenticates the affiliate JWT
 * ({@code ROLE_AFFILIATE}), gates the prefix, and relays the machine token plus the
 * validated identity as {@code X-User-Id} — exactly the admin-prefix pattern. The
 * identity header is ALWAYS the scope: no endpoint takes a user id parameter, so a
 * caller can only ever read their own data (no IDOR surface). Defense in depth: every
 * endpoint re-checks the caller is a LIVE promoter ({@code is_promoter=1}, not
 * deleted) and 403s with errno {@link AffiliateErrno#NOT_PROMOTER} otherwise.
 *
 * <p>Envelope + errno table are the handoff contract for the gateway-admin SPA:
 * {@code docs/handoff-affiliate-portal.md}.
 */
@RestController
@RequestMapping("/srv/private/affiliate")
public class LitemallAffiliateRestController {

    private final LitemallAffiliateServiceLayer affiliateService;
    private final LitemallExtractServiceLayer extractServiceLayer;

    /** Customer storefront origin used to mint shareable invite links. */
    @Value("${litemall.affiliate.storefront-base-url:http://localhost:9000}")
    private String storefrontBaseUrl;

    public LitemallAffiliateRestController(LitemallAffiliateServiceLayer affiliateService,
                                           LitemallExtractServiceLayer extractServiceLayer) {
        this.affiliateService = affiliateService;
        this.extractServiceLayer = extractServiceLayer;
    }

    /**
     * {@code GET /dashboard} — headline sums:
     * {@code { available, frozenSum, lifetimeEarned, thisMonth, spreadCount, referredOrders }}.
     */
    @GetMapping("/dashboard")
    public Object dashboard(@RequestHeader("X-User-Id") Integer userId) {
        Object gate = requirePromoter(userId);
        if (gate != null) {
            return gate;
        }
        LitemallAffiliateServiceLayer.Dashboard dashboard = affiliateService.dashboard(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("available", dashboard.available());
        data.put("frozenSum", dashboard.frozenSum());
        data.put("lifetimeEarned", dashboard.lifetimeEarned());
        data.put("thisMonth", dashboard.thisMonth());
        data.put("spreadCount", dashboard.spreadCount());
        data.put("referredOrders", dashboard.referredOrders());
        return ResponseUtil.ok(data);
    }

    /** {@code GET /records?page=&limit=} — the caller's commission ledger, newest first. */
    @GetMapping("/records")
    public Object records(@RequestHeader("X-User-Id") Integer userId,
                          @RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "10") Integer limit) {
        Object gate = requirePromoter(userId);
        if (gate != null) {
            return gate;
        }
        if (page < 1 || limit < 1 || limit > 100) {
            return badRequest("page must be >= 1 and limit in 1..100");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LitemallUserBrokerageRecord record : affiliateService.records(userId, page, limit)) {
            rows.add(recordDto(record));
        }
        return ResponseUtil.ok(paged(rows, affiliateService.countRecords(userId), page, limit));
    }

    /** {@code GET /team?page=&limit=} — users referred by the caller (masked nicknames). */
    @GetMapping("/team")
    public Object team(@RequestHeader("X-User-Id") Integer userId,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        Object gate = requirePromoter(userId);
        if (gate != null) {
            return gate;
        }
        if (page < 1 || limit < 1 || limit > 100) {
            return badRequest("page must be >= 1 and limit in 1..100");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LitemallUser member : affiliateService.team(userId, page, limit)) {
            Map<String, Object> dto = new HashMap<>();
            // PII boundary: a promoter sees a masked handle, never the real
            // nickname/mobile/email of the people they referred.
            dto.put("nickname", maskNickname(member.getNickname()));
            dto.put("addTime", member.getAddTime());
            dto.put("payCount", member.getPayCount() == null ? 0 : member.getPayCount());
            rows.add(dto);
        }
        return ResponseUtil.ok(paged(rows, affiliateService.countTeam(userId), page, limit));
    }

    /** {@code GET /links} — the caller's invite code + canonical share URLs. */
    @GetMapping("/links")
    public Object links(@RequestHeader("X-User-Id") Integer userId) {
        Object gate = requirePromoter(userId);
        if (gate != null) {
            return gate;
        }
        String code = InviteCodes.encode(userId);
        String base = storefrontBaseUrl.endsWith("/")
                ? storefrontBaseUrl.substring(0, storefrontBaseUrl.length() - 1)
                : storefrontBaseUrl;
        Map<String, Object> data = new HashMap<>();
        data.put("inviteCode", code);
        data.put("registerUrl", base + "/register?invite=" + code);
        // Template: the SPA substitutes a concrete goods id for {goodsId}.
        data.put("productUrlTemplate", base + "/product/{goodsId}?invite=" + code);
        return ResponseUtil.ok(data);
    }

    /**
     * {@code POST /extract} — request a brokerage withdrawal. Body:
     * {@code { realName, extractType, bankCode, bankAddress, extractAmount }}.
     * The funding source is FORCED to brokerage here; wallet withdrawals keep their
     * existing {@code /srv/wallet/extract} surface.
     */
    @PostMapping("/extract")
    public Object extract(@RequestHeader("X-User-Id") Integer userId,
                          @RequestBody LitemallExtractRequestCommand command) {
        Object gate = requirePromoter(userId);
        if (gate != null) {
            return gate;
        }
        command.setUserId(userId);
        command.setSource(LitemallExtractRequestCommand.SOURCE_BROKERAGE);
        try {
            LitemallExtractAggregate extract = extractServiceLayer.requestExtract(command);
            Map<String, Object> data = new HashMap<>();
            data.put("id", extract.getExtractId().getId());
            data.put("extractAmount", extract.getExtractAmount().getAmount());
            data.put("balanceAfter", extract.getBalanceAfter().getAmount());
            data.put("status", extract.getStatus().getCode());
            return ResponseUtil.ok(data);
        } catch (LitemallBrokerageBelowMinimumException e) {
            return unprocessable(AffiliateErrno.EXTRACT_BELOW_MINIMUM, e.getMessage());
        } catch (LitemallBrokerageInsufficientException e) {
            return unprocessable(AffiliateErrno.EXTRACT_INSUFFICIENT, e.getMessage());
        }
    }

    /** {@code GET /extract/history} — the caller's brokerage withdrawals, newest first. */
    @GetMapping("/extract/history")
    public Object extractHistory(@RequestHeader("X-User-Id") Integer userId) {
        Object gate = requirePromoter(userId);
        if (gate != null) {
            return gate;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LitemallUserExtract extract : affiliateService.brokerageExtractHistory(userId)) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", extract.getId());
            dto.put("realName", extract.getRealName());
            dto.put("extractType", extract.getExtractType());
            dto.put("bankCode", extract.getBankCode());
            dto.put("bankAddress", extract.getBankAddress());
            dto.put("extractPrice", extract.getExtractPrice());
            dto.put("balance", extract.getBalance());
            dto.put("status", extract.getStatus());
            dto.put("failMsg", extract.getFailMsg());
            dto.put("failTime", extract.getFailTime());
            dto.put("addTime", extract.getAddTime());
            rows.add(dto);
        }
        return ResponseUtil.ok(rows);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Non-null result = the 403 response to return; null = caller is a live promoter. */
    private Object requirePromoter(Integer userId) {
        if (affiliateService.findLivePromoter(userId) == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ResponseUtil.fail(AffiliateErrno.NOT_PROMOTER, "Not an affiliate"));
        }
        return null;
    }

    private Map<String, Object> recordDto(LitemallUserBrokerageRecord record) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", record.getId());
        dto.put("title", record.getTitle());
        dto.put("price", record.getPrice());
        dto.put("pm", Boolean.TRUE.equals(record.getPm()) ? 1 : 0);
        dto.put("status", record.getStatus());
        dto.put("statusText", statusText(record.getStatus()));
        dto.put("linkType", record.getLinkType());
        dto.put("linkId", record.getLinkId());
        dto.put("mark", record.getMark());
        dto.put("addTime", record.getAddTime());
        dto.put("freezeTime", record.getFreezeTime());
        dto.put("unfreezeTime", record.getUnfreezeTime());
        return dto;
    }

    private static String statusText(Byte status) {
        if (status == null) {
            return "unknown";
        }
        switch (status) {
            case LitemallUserBrokerageRecord.STATUS_FROZEN: return "frozen";
            case LitemallUserBrokerageRecord.STATUS_VALID: return "valid";
            case LitemallUserBrokerageRecord.STATUS_INVALID: return "invalid";
            default: return "unknown";
        }
    }

    private static String maskNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "Anonymous";
        }
        String trimmed = nickname.trim();
        return trimmed.charAt(0) + "***";
    }

    private static Map<String, Object> paged(List<?> rows, long total, int page, int limit) {
        Map<String, Object> data = new HashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("limit", limit);
        data.put("pages", limit == 0 ? 0 : (int) Math.ceil((double) total / limit));
        return data;
    }

    private static Object badRequest(String message) {
        return ResponseEntity.badRequest().body(ResponseUtil.fail(AffiliateErrno.BAD_REQUEST, message));
    }

    private static Object unprocessable(int errno, String message) {
        return ResponseEntity.unprocessableEntity().body(ResponseUtil.fail(errno, message));
    }
}
