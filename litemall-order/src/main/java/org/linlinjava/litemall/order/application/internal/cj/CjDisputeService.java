package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjDisputeException;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.cj.LitemallCjDisputeCancelledEvent;
import org.linlinjava.litemall.order.domain.events.cj.LitemallCjDisputeOpenedEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCjDisputeAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCjDisputeRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.cj.CjDisputeExpectation;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjDisputeFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputableLine;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputeOpenCommand;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputeQuote;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputeSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service for customer CJ disputes — a thin orchestrator (Millett/Tune
 * ch. 25): ownership/state guards live on the aggregates, CJ translation lives in the
 * {@link CjDisputeFacade} ACL, persistence in the repository. CJ owns the dispute
 * lifecycle, so reads lazily reconcile the local projection from CJ (eventual
 * consistency across the context boundary) instead of pretending to own the truth.
 */
@Service
public class CjDisputeService {

    private static final Logger log = LoggerFactory.getLogger(CjDisputeService.class);

    private final LitemallOrderRepository orderRepository;
    private final LitemallCjDisputeRepository disputeRepository;
    private final CjDisputeFacade disputeFacade;
    private final LitemallDomainEventPublisher domainEventPublisher;

    public CjDisputeService(LitemallOrderRepository orderRepository,
                            LitemallCjDisputeRepository disputeRepository,
                            CjDisputeFacade disputeFacade,
                            LitemallDomainEventPublisher domainEventPublisher) {
        this.orderRepository = orderRepository;
        this.disputeRepository = disputeRepository;
        this.disputeFacade = disputeFacade;
        this.domainEventPublisher = domainEventPublisher;
    }

    /** Everything the "report a problem" form needs: disputable lines + reasons + limits. */
    public DisputeContext getContext(LitemallUserId userId, LitemallOrderId orderId) {
        LitemallOrderAggregate order = ownedDisputableOrder(userId, orderId);
        List<CjDisputableLine> lines = disputeFacade.disputableLines(order.getCjOrderId());
        List<CjDisputableLine> eligible = lines.stream()
                .filter(CjDisputableLine::isDisputable)
                .collect(Collectors.toList());
        if (eligible.isEmpty()) {
            throw new LitemallCjDisputeException(
                    "CJ reports no disputable items on this order right now");
        }
        CjDisputeQuote quote = disputeFacade.quote(order.getCjOrderId(), eligible.stream()
                .map(l -> CjDisputeOpenCommand.Line.builder()
                        .lineItemId(l.getLineItemId())
                        .quantity(l.getMaxQuantity())
                        .unitPriceUsd(l.getUnitPriceUsd())
                        .build())
                .collect(Collectors.toList()));
        return new DisputeContext(eligible, quote);
    }

    /**
     * Open a dispute: guards -> CJ create (idempotent businessDisputeId) -> local row +
     * opened-event, all in one transaction so a CJ rejection leaves nothing behind.
     * Line quantities/prices are re-read from CJ server-side — the client only ever
     * selects lineItemIds and quantities, never prices.
     */
    @Transactional
    public LitemallCjDisputeAggregate openDispute(LitemallUserId userId, LitemallOrderId orderId,
                                                  OpenDisputeCommand command) {
        LitemallOrderAggregate order = ownedDisputableOrder(userId, orderId);
        if (!disputeRepository.findOpenByOrder(orderId).isEmpty()) {
            throw new LitemallCjDisputeException(
                    "There is already an open dispute for this order — cancel it before opening another");
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new LitemallCjDisputeException("Pick at least one item to dispute");
        }
        if (command.message() == null || command.message().isBlank()) {
            throw new LitemallCjDisputeException("Describe the problem so CJ can assess it");
        }

        // Server-side truth for line prices/limits (never trust client amounts).
        Map<String, CjDisputableLine> byLineItemId = disputeFacade.disputableLines(order.getCjOrderId()).stream()
                .filter(CjDisputableLine::isDisputable)
                .collect(Collectors.toMap(CjDisputableLine::getLineItemId, Function.identity()));
        List<CjDisputeOpenCommand.Line> lines = new ArrayList<>();
        for (OpenDisputeCommand.Line requested : command.lines()) {
            CjDisputableLine cjLine = byLineItemId.get(requested.lineItemId());
            if (cjLine == null) {
                throw new LitemallCjDisputeException(
                        "Item " + requested.lineItemId() + " is not disputable on this order");
            }
            int quantity = Math.max(1, Math.min(requested.quantity(), cjLine.getMaxQuantity()));
            lines.add(CjDisputeOpenCommand.Line.builder()
                    .lineItemId(cjLine.getLineItemId())
                    .quantity(quantity)
                    .unitPriceUsd(cjLine.getUnitPriceUsd())
                    .build());
        }

        // Deterministic merchant key: a retry after a lost response reuses the same id
        // and CJ dedupes instead of double-filing (order_sn is unique per order).
        String businessDisputeId = order.getOrderSn() + "-D" + (disputeRepository.findByOrder(orderId).size() + 1);

        disputeFacade.open(CjDisputeOpenCommand.builder()
                .cjOrderId(order.getCjOrderId())
                .businessDisputeId(businessDisputeId)
                .reasonId(command.reasonId())
                .expectation(command.expectation())
                .message(command.message())
                .imageUrls(command.imageUrls())
                .lines(lines)
                .build());

        LitemallCjDisputeAggregate dispute = new LitemallCjDisputeAggregate();
        dispute.setOrderId(orderId);
        dispute.setUserId(userId);
        dispute.setCjOrderId(order.getCjOrderId());
        dispute.setBusinessDisputeId(businessDisputeId);
        dispute.setReasonId(command.reasonId());
        dispute.setReasonName(command.reasonName());
        dispute.setExpectation(command.expectation());
        dispute.setMessage(command.message());
        dispute.setImageUrls(command.imageUrls());
        dispute.setCjStatus("Processing");
        disputeRepository.add(dispute);

        domainEventPublisher.publish(new LitemallCjDisputeOpenedEvent(
                orderId, businessDisputeId, command.expectation()));
        log.info("CJ dispute {} opened for order {} (cjOrderId={})",
                businessDisputeId, orderId.getId(), order.getCjOrderId());
        return dispute;
    }

    /**
     * The order's disputes, lazily reconciled with CJ: back-fills cj_dispute_id on rows
     * CJ has since registered and refreshes status/resolution on open ones. A CJ outage
     * degrades to the local projection instead of failing the read.
     */
    @Transactional
    public List<LitemallCjDisputeAggregate> listDisputes(LitemallUserId userId, LitemallOrderId orderId) {
        LitemallOrderAggregate order = ownedOrder(userId, orderId);
        List<LitemallCjDisputeAggregate> local = disputeRepository.findByOrder(orderId);
        boolean needsSync = local.stream().anyMatch(d -> d.isOpen() || d.getCjDisputeId() == null);
        if (!needsSync || order.getCjOrderId() == null) {
            return local;
        }
        try {
            reconcile(local, disputeFacade.fetchDisputes(order.getCjOrderId()));
        } catch (LitemallCjDisputeException e) {
            log.warn("CJ dispute refresh failed for order {}; serving local projection: {}",
                    orderId.getId(), e.getMessage());
        }
        return local;
    }

    /** Withdraw an open dispute: CJ cancel first, then the local mark — one transaction. */
    @Transactional
    public void cancelDispute(LitemallUserId userId, LitemallOrderId orderId, Integer disputeId) {
        LitemallOrderAggregate order = ownedOrder(userId, orderId);
        LitemallCjDisputeAggregate dispute = disputeRepository.findById(disputeId)
                .filter(d -> d.getOrderId().equals(orderId) && d.getUserId().getId().equals(userId.getId()))
                .orElseThrow(() -> new LitemallCjDisputeException("Dispute not found"));
        dispute.markCancelled(); // guard: must still be open

        if (dispute.getCjDisputeId() == null) {
            // CJ hasn't surfaced its id yet — try to reconcile once before cancelling.
            reconcile(List.of(dispute), disputeFacade.fetchDisputes(order.getCjOrderId()));
            if (dispute.getCjDisputeId() == null) {
                throw new LitemallCjDisputeException(
                        "CJ is still registering this dispute; try cancelling again in a moment");
            }
        }
        disputeFacade.cancel(order.getCjOrderId(), dispute.getCjDisputeId());
        disputeRepository.markCancelled(dispute.getDisputeId());
        domainEventPublisher.publish(new LitemallCjDisputeCancelledEvent(
                orderId, dispute.getBusinessDisputeId()));
    }

    /**
     * Fold CJ's list into the local rows: match by cj_dispute_id; rows CJ has not been
     * matched to yet (cj id still null) absorb the unclaimed CJ entries oldest-first
     * (the open-dispute guard keeps this unambiguous in practice).
     */
    private void reconcile(List<LitemallCjDisputeAggregate> local, List<CjDisputeSnapshot> remote) {
        List<CjDisputeSnapshot> unclaimed = new ArrayList<>(remote);
        for (LitemallCjDisputeAggregate dispute : local) {
            if (dispute.getCjDisputeId() == null) {
                continue;
            }
            unclaimed.stream()
                    .filter(s -> dispute.getCjDisputeId().equals(s.getCjDisputeId()))
                    .findFirst()
                    .ifPresent(s -> {
                        apply(dispute, s);
                        unclaimed.remove(s);
                    });
        }
        List<LitemallCjDisputeAggregate> pending = local.stream()
                .filter(d -> d.getCjDisputeId() == null && !d.isCancelled())
                .collect(Collectors.toList());
        for (int i = 0; i < pending.size() && i < unclaimed.size(); i++) {
            apply(pending.get(i), unclaimed.get(i));
        }
    }

    private void apply(LitemallCjDisputeAggregate dispute, CjDisputeSnapshot snapshot) {
        dispute.applyCjView(snapshot.getCjDisputeId(), snapshot.getStatus(), snapshot.getResolution(),
                snapshot.getRefundAmountUsd(), snapshot.getResendOrderCode());
        disputeRepository.updateCjProjection(dispute);
    }

    private LitemallOrderAggregate ownedDisputableOrder(LitemallUserId userId, LitemallOrderId orderId) {
        LitemallOrderAggregate order = ownedOrder(userId, orderId);
        LitemallCjDisputeAggregate.assertDisputable(order, userId);
        return order;
    }

    private LitemallOrderAggregate ownedOrder(LitemallUserId userId, LitemallOrderId orderId) {
        LitemallOrderAggregate order = orderRepository.findByIdAndUserId(userId, orderId);
        if (order == null) {
            throw new LitemallCjDisputeException("Order not found");
        }
        return order;
    }

    /** Context payload for the dispute form. */
    public record DisputeContext(List<CjDisputableLine> lines, CjDisputeQuote quote) {
    }

    /** Customer input for opening a dispute (quantities only — prices come from CJ). */
    public record OpenDisputeCommand(int reasonId, String reasonName, CjDisputeExpectation expectation,
                                     String message, List<String> imageUrls, List<Line> lines) {
        public record Line(String lineItemId, int quantity) {
        }
    }
}
