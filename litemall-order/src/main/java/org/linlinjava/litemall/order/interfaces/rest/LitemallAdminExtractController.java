package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallUserExtract;
import org.linlinjava.litemall.order.application.internal.LitemallExtractServiceLayer;
import org.linlinjava.litemall.order.interfaces.util.AffiliateErrno;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin withdrawal console under {@code /srv/private/admin/extract/**} (Wave 5) —
 * same admin namespace and trust model as {@link LitemallAdminAftersaleController}:
 * the edge gateway gates the prefix to ROLE_ADMIN and relays the machine token;
 * this controller adds no auth of its own.
 *
 * <p>Money was debited at request time (wallet or brokerage), so APPROVE is
 * bookkeeping only; REJECT refunds the debited amount to the balance it came from
 * (the pm=0 brokerage ledger row is the source marker). Both transitions are
 * guarded on PENDING — a raced double-click surfaces as a clean 422, never a
 * double refund.
 */
@RestController
@RequestMapping("/srv/private/admin/extract")
public class LitemallAdminExtractController {

    private final LitemallExtractServiceLayer extractServiceLayer;

    public LitemallAdminExtractController(LitemallExtractServiceLayer extractServiceLayer) {
        this.extractServiceLayer = extractServiceLayer;
    }

    /**
     * Paged withdrawal queue, newest first; optional status filter
     * (-1 rejected, 0 pending, 1 processing, 2 completed).
     * Returns {@code { list, total, page, limit, pages }}.
     */
    @GetMapping("/list")
    public Object list(@RequestParam(required = false) Byte status,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LitemallUserExtract extract : extractServiceLayer.adminList(status, page, limit)) {
            rows.add(toDto(extract));
        }
        long total = extractServiceLayer.adminCount(status);

        Map<String, Object> data = new HashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("limit", limit);
        data.put("pages", limit == 0 ? 0 : (int) Math.ceil((double) total / limit));
        return ResponseUtil.ok(data);
    }

    /** Approve a PENDING withdrawal (guarded 0 → 2; payout itself is out of band). */
    @PostMapping("/{id}/approve")
    public Object approve(@PathVariable Integer id) {
        try {
            extractServiceLayer.approveExtract(id);
            return ResponseUtil.ok();
        } catch (IllegalStateException e) {
            return unprocessable(AffiliateErrno.EXTRACT_INVALID_STATE, e.getMessage());
        }
    }

    /**
     * Reject a PENDING withdrawal (guarded 0 → -1 + fail_msg/fail_time) and refund
     * the amount to its funding balance. Body: {@code { "reason": "..." }}.
     */
    @PostMapping("/{id}/reject")
    public Object reject(@PathVariable Integer id,
                         @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        try {
            extractServiceLayer.rejectExtract(id, reason);
            return ResponseUtil.ok();
        } catch (IllegalStateException e) {
            return unprocessable(AffiliateErrno.EXTRACT_INVALID_STATE, e.getMessage());
        }
    }

    private Map<String, Object> toDto(LitemallUserExtract extract) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", extract.getId());
        dto.put("userId", extract.getUserId());
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
        // wallet | brokerage — resolved from the pm=0 ledger marker row.
        dto.put("source", extractServiceLayer.sourceOf(extract.getId()));
        return dto;
    }

    private static ResponseEntity<Object> unprocessable(int errno, String message) {
        return ResponseEntity.unprocessableEntity().body(ResponseUtil.fail(errno, message));
    }
}
