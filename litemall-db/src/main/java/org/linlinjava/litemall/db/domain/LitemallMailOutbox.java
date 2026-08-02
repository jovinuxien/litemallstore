package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * One queued customer email (table {@code litemall_mail_outbox}, created in V41 —
 * Wave 6 transactional mail).
 *
 * <p>Hand-written (not MyBatis-Generator output) and co-located with the generated
 * domains, like {@link LitemallCjSourcingRequest}. Rows are enqueued by order's
 * AFTER_COMMIT listeners (or any writer needing scheduled mail via a future
 * {@code sendAt}) and delivered by the outbox sweep through litemall-core's
 * {@code CustomerMailSender}.
 */
public class LitemallMailOutbox {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_FAILED = "failed";

    private Integer id;
    /** Destination email address, captured at enqueue time. */
    private String recipient;
    private String subject;
    /** Rendered plain-text body. */
    private String body;
    /** Rendered HTML body (V48); NULL = plain-text-only mail. */
    private String bodyHtml;
    /** Shared template key (order-confirmation|shipped|refund-approved|pickup-code|password-reset). */
    private String templateKey;
    /** pending | sent | failed. */
    private String status;
    /** Delivery attempts so far; the sweep flips the row to failed at the cap. */
    private Integer attempts;
    /** Earliest delivery time — the scheduled-send seam (defaults to enqueue time). */
    private LocalDateTime sendAt;
    /** Last delivery error, truncated to the column width. */
    private String lastError;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getBodyHtml() { return bodyHtml; }
    public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }

    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public LocalDateTime getSendAt() { return sendAt; }
    public void setSendAt(LocalDateTime sendAt) { this.sendAt = sendAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public LocalDateTime getAddTime() { return addTime; }
    public void setAddTime(LocalDateTime addTime) { this.addTime = addTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
