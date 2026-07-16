package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.dao.MailOutboxMapper;
import org.linlinjava.litemall.db.domain.LitemallMailOutbox;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin mail-outbox surface under {@code /srv/private/admin/mail/**} — same admin
 * namespace and trust model as {@link LitemallAdminOrderController}: the edge
 * gateway gates the prefix to ROLE_ADMIN and relays the validated identity
 * (machine token); this controller adds no auth of its own.
 *
 * <p>Contract for gateway-admin: {@code docs/handoff-mail-outbox-admin.md}.
 */
@RestController
@RequestMapping("/srv/private/admin/mail")
public class LitemallAdminMailController {

    private final MailOutboxMapper mailOutboxMapper;

    public LitemallAdminMailController(MailOutboxMapper mailOutboxMapper) {
        this.mailOutboxMapper = mailOutboxMapper;
    }

    /**
     * Paged outbox, newest first, optional status filter
     * ({@code pending|sent|failed}). Returns { list, total, page, limit, pages }.
     */
    @GetMapping("/list")
    public Object list(@RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit) {
        if (status != null && !status.isEmpty()
                && !LitemallMailOutbox.STATUS_PENDING.equals(status)
                && !LitemallMailOutbox.STATUS_SENT.equals(status)
                && !LitemallMailOutbox.STATUS_FAILED.equals(status)) {
            return ResponseUtil.badArgumentValue();
        }
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 10 : Math.min(limit, 100);

        List<LitemallMailOutbox> rows = mailOutboxMapper.selectPage(status, (safePage - 1) * safeLimit, safeLimit);
        int total = mailOutboxMapper.countByStatus(status);

        Map<String, Object> data = new HashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.put("page", safePage);
        data.put("limit", safeLimit);
        data.put("pages", (int) Math.ceil((double) total / safeLimit));
        return ResponseUtil.ok(data);
    }

    /**
     * Resend a FAILED row: guarded reset to pending with attempts zeroed,
     * last_error cleared and send_at=now, so the next sweep delivers it.
     * 422 when the row is missing or not in failed state.
     */
    @PostMapping("/{id}/resend")
    public ResponseEntity<Object> resend(@PathVariable Integer id) {
        int updated = mailOutboxMapper.resetForResend(id, LocalDateTime.now());
        if (updated == 0) {
            return ResponseEntity.unprocessableEntity()
                    .body(ResponseUtil.fail(422, "Only failed outbox rows can be resent"));
        }
        return ResponseEntity.ok(ResponseUtil.ok(mailOutboxMapper.selectById(id)));
    }
}
