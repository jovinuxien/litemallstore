package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.application.internal.aftersale.LitemallAftersaleServiceLayer;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallAftersaleException;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.interfaces.dtos.aftersale.AftersaleDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderOperationDtoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.linlinjava.litemall.order.interfaces.util.LitemallHttpResponseUtil.buildResponse;

/**
 * Admin aftersale queue under {@code /srv/private/admin/aftersale/**} — same admin
 * namespace and trust model as {@code LitemallAdminOrderController}: the edge
 * gateway gates the prefix to ROLE_ADMIN and relays the validated identity
 * (machine token); this controller adds no auth of its own.
 *
 * <p>Approval flows into the tender-parity refund path in ONE transaction (wallet
 * credit capped at min(requested, paid, captured); CARD stays a PSP seam); reject
 * leaves the order's money and status untouched.
 */
@RestController
@RequestMapping("/srv/private/admin/aftersale")
public class LitemallAdminAftersaleController {

    private final LitemallAftersaleServiceLayer aftersaleService;
    private final LitemallOrderOrchestratorService orchestrator;

    public LitemallAdminAftersaleController(LitemallAftersaleServiceLayer aftersaleService,
                                            LitemallOrderOrchestratorService orchestrator) {
        this.aftersaleService = aftersaleService;
        this.orchestrator = orchestrator;
    }

    /** Paged queue, newest first. Optional status/orderId/userId filters. Returns { list, total, page, limit, pages }. */
    @GetMapping("/list")
    public Object list(@RequestParam(required = false) Short status,
                       @RequestParam(required = false) Integer orderId,
                       @RequestParam(required = false) Integer userId,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        List<AftersaleDtoResponse> rows = aftersaleService
                .adminList(status, orderId, userId, page, limit).stream()
                .map(AftersaleDtoResponse::fromDomain)
                .collect(Collectors.toList());
        long total = aftersaleService.adminCount(status, orderId, userId);

        Map<String, Object> data = new HashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.put("page", page);
        data.put("limit", limit);
        data.put("pages", limit == 0 ? 0 : (int) Math.ceil((double) total / limit));
        return ResponseUtil.ok(data);
    }

    /** Approve: accept the application and refund via the tender-parity path (one transaction). */
    @PostMapping("/{aftersaleId}/approve")
    public ResponseEntity<OrderOperationDtoResponse> approve(@PathVariable Integer aftersaleId) {
        try {
            LitemallOrderOperationResult result = orchestrator.approveAftersale(aftersaleId);
            return buildResponse(result);
        } catch (LitemallAftersaleException e) {
            return buildResponse(LitemallOrderOperationResult.submitFailed(e.getMessage()));
        }
    }

    /** Reject: the application closes, the order keeps its money and status. */
    @PostMapping("/{aftersaleId}/reject")
    public ResponseEntity<OrderOperationDtoResponse> reject(@PathVariable Integer aftersaleId,
                                                            @RequestBody(required = false) Map<String, String> body) {
        try {
            String reason = body == null ? null : body.get("reason");
            LitemallOrderOperationResult result = orchestrator.rejectAftersale(aftersaleId, reason);
            return buildResponse(result);
        } catch (LitemallAftersaleException e) {
            return buildResponse(LitemallOrderOperationResult.submitFailed(e.getMessage()));
        }
    }

    // ---- batch decisions (Wave 4, Task C) ---------------------------------------------

    /**
     * Approve many applications in one call. The loop calls the orchestrator PER ID —
     * each call is its own transaction (this controller sits outside the orchestrator's
     * proxy), so one bad id can never roll back its siblings' refunds. Partial-success
     * envelope: {@code {succeeded: [id], failed: [{id, errmsg}]}}.
     */
    @PostMapping("/batch-approve")
    public Object batchApprove(@RequestBody BatchRequest request) {
        return batch(request, id -> orchestrator.approveAftersale(id));
    }

    /** Reject many applications; same per-id-transaction + partial-success semantics. */
    @PostMapping("/batch-reject")
    public Object batchReject(@RequestBody BatchRequest request) {
        String reason = request == null ? null : request.getReason();
        return batch(request, id -> orchestrator.rejectAftersale(id, reason));
    }

    private Object batch(BatchRequest request,
                         java.util.function.Function<Integer, LitemallOrderOperationResult> op) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(ResponseUtil.fail(422, "ids is required"));
        }
        List<Integer> succeeded = new java.util.ArrayList<>();
        List<Map<String, Object>> failed = new java.util.ArrayList<>();
        for (Integer id : request.getIds()) {
            if (id == null) {
                continue;
            }
            try {
                LitemallOrderOperationResult result = op.apply(id);
                if (result.isSuccess()) {
                    succeeded.add(id);
                } else {
                    failed.add(failure(id, result.getMessage()));
                }
            } catch (Exception e) {
                // Typed refusals (LitemallAftersaleException etc.) and hard errors alike:
                // this id failed, ITS transaction rolled back, the loop continues.
                failed.add(failure(id, e.getMessage()));
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("succeeded", succeeded);
        data.put("failed", failed);
        return ResponseUtil.ok(data);
    }

    private static Map<String, Object> failure(Integer id, String errmsg) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("id", id);
        entry.put("errmsg", errmsg == null ? "operation failed" : errmsg);
        return entry;
    }

    /** {@code {ids: [1,2,3], reason: "..."}} — reason applies to batch-reject only. */
    @lombok.Data
    public static class BatchRequest {
        private List<Integer> ids;
        private String reason;
    }
}
