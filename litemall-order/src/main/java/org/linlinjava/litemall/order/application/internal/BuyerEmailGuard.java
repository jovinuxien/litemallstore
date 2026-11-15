package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.db.dao.LitemallUserMapper;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Submit-time guarantee that the ordering account has an email address
 * (plan-order-email-guarantee-2026-11-12.md, step 2).
 *
 * <p>Every customer mail (confirmation, shipped, delivered, refund) is addressed to
 * {@code litemall_user.email}, and CJ placement sends the same address. Before this
 * guard only the browser form asked for one: an account without an email could place
 * and pay, then be skipped by the mail pipeline and placed at CJ under the store's
 * fallback address.
 *
 * <p>Called from the orchestrator PRE-CHECK zone, outside the placement transaction, so
 * a refusal is a clean 422 submit-failed envelope (never the rollback-only 502).
 *
 * <p>Kill switch {@code litemall.order.require-buyer-email} (env
 * {@code LITEMALL_ORDER_REQUIRE_BUYER_EMAIL}, default true): false restores the previous
 * behaviour by a container recreate alone. A failed lookup never blocks a sale — it
 * logs and lets the submit through.
 */
@Service
public class BuyerEmailGuard {

    private static final Logger log = LoggerFactory.getLogger(BuyerEmailGuard.class);

    /** The customer-facing refusal. The storefront renders it verbatim. */
    public static final String REFUSAL =
            "Add an email address to place this order — we send your order confirmation and tracking updates there.";

    private final LitemallUserMapper userMapper;
    private final boolean required;

    public BuyerEmailGuard(LitemallUserMapper userMapper,
                           @Value("${litemall.order.require-buyer-email:true}") boolean required) {
        this.userMapper = userMapper;
        this.required = required;
        log.info("Buyer email at submit: {}", required ? "REQUIRED" : "NOT required (kill switch off)");
    }

    /** The refusal message when this account may not place an order; empty when it may. */
    public Optional<String> refusalFor(Integer userId) {
        if (!required || userId == null) {
            return Optional.empty();
        }
        LitemallUser user;
        try {
            user = userMapper.selectByPrimaryKey(userId);
        } catch (RuntimeException ex) {
            log.warn("Buyer email check skipped for user {}: lookup failed ({})", userId, ex.toString());
            return Optional.empty();
        }
        if (user == null) {
            // Not this guard's question: placeOrder decides what an unknown user means.
            return Optional.empty();
        }
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            log.warn("Order submit refused for user {}: no email address on the account", userId);
            return Optional.of(REFUSAL);
        }
        return Optional.empty();
    }
}
