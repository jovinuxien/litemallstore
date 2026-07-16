package org.linlinjava.litemall.order.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.db.dao.LitemallUserBrokerageRecordMapper;
import org.linlinjava.litemall.db.dao.LitemallUserExtractMapper;
import org.linlinjava.litemall.db.dao.LitemallUserMapper;
import org.linlinjava.litemall.db.domain.LitemallUserBrokerageRecord;
import org.linlinjava.litemall.db.domain.LitemallUserExtract;
import org.linlinjava.litemall.order.application.util.exception.wallet.LitemallBrokerageBelowMinimumException;
import org.linlinjava.litemall.order.application.util.exception.wallet.LitemallBrokerageInsufficientException;
import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.wallet.LitemallExtractRequestedEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallExtractAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallWalletAggregate;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallExtractRequestCommand;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletCreditCommand;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletDebitCommand;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallExtractRepository;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallWalletRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.service.wallet.LitemallWalletDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@Transactional
public class LitemallExtractServiceLayer {

    private final LitemallExtractRepository extractRepository;
    private final LitemallWalletRepository walletRepository;
    private final LitemallWalletServiceImpl walletService;
    private final LitemallWalletDomainService walletDomainService;

    // Wave 5 brokerage withdrawals: the guarded balance movement + ledger live on
    // the shared mappers (same layering as the other application/internal services).
    @Autowired
    private LitemallUserMapper userMapper;
    @Autowired
    private LitemallUserBrokerageRecordMapper brokerageRecordMapper;
    @Autowired
    private LitemallUserExtractMapper extractMapper;
    @Autowired
    private BrokerageService brokerageService;

    @Autowired
    private LitemallDomainEventPublisher domainEventPublisher;

    public LitemallExtractServiceLayer(LitemallExtractRepository extractRepository,
                                        LitemallWalletRepository walletRepository,
                                        LitemallWalletServiceImpl walletService,
                                        LitemallWalletDomainService walletDomainService) {
        this.extractRepository = extractRepository;
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.walletDomainService = walletDomainService;
    }

    /**
     * Request an extract (withdrawal). {@code source} picks the funding balance:
     * wallet ({@code now_money}, the pre-Wave-5 flow, default) or brokerage
     * ({@code brokerage_price}, Wave 5 affiliate earnings). Either way the money is
     * debited up front and the row waits for admin approval; a rejection refunds it.
     *
     * @param command the extract request command
     * @return the created extract aggregate
     */
    public LitemallExtractAggregate requestExtract(LitemallExtractRequestCommand command) {
        if (command.isBrokerageSource()) {
            return requestBrokerageExtract(command);
        }
        LitemallUserId userId = new LitemallUserId(command.getUserId());
        LitemallMoney extractAmount = new LitemallMoney(command.getExtractAmount());

        // Load wallet and validate
        LitemallWalletAggregate wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for user: " + command.getUserId()));

        walletDomainService.validateExtractRequest(wallet, extractAmount);

        // Compute balance after extract
        LitemallMoney balanceAfter = wallet.getBalance().subtract(extractAmount);

        // Create extract aggregate
        LitemallExtractAggregate extract = new LitemallExtractAggregate(
                userId,
                command.getRealName(),
                command.getExtractType(),
                command.getBankCode(),
                command.getBankAddress(),
                extractAmount,
                balanceAfter
        );
        extractRepository.add(extract);

        // Debit wallet. linkId = the extract row id: litemall_user_bill.link_id is
        // NOT NULL, so the historical null here made EVERY wallet extract 500 at the
        // bill insert (latent since the wallet vertical was absorbed — the path had
        // never been exercised; surfaced by the Wave-5 extract e2e).
        LitemallWalletDebitCommand debitCommand = new LitemallWalletDebitCommand(
                command.getUserId(),
                extractAmount.getAmount(),
                "Withdrawal",
                "extract",
                command.getExtractType(),
                extract.getExtractId() != null ? String.valueOf(extract.getExtractId().getId()) : "0",
                "Wallet withdrawal request"
        );
        walletService.debit(debitCommand);

        // Publish event
        if (extract.getExtractId() != null) {
            domainEventPublisher.publish(new LitemallExtractRequestedEvent(
                    extract.getExtractId(),
                    userId,
                    extractAmount
            ));
        }

        log.info("Extract requested for userId={}, amount={}", command.getUserId(), command.getExtractAmount());
        return extract;
    }

    /**
     * Brokerage-sourced withdrawal (one transaction): floor check against
     * {@code litemall_brokerage_min_extract}, guarded {@code debitBrokerage} (a
     * 0-row update = insufficient balance or lost race), the pending
     * {@code litemall_user_extract} row, and the {@code pm=0} ledger entry that also
     * marks the extract as brokerage-sourced for the admin reject path.
     *
     * <p>Both business rejections throw {@link IllegalStateException} so the wallet
     * REST layer maps them to 422 (its existing convention).
     */
    private LitemallExtractAggregate requestBrokerageExtract(LitemallExtractRequestCommand command) {
        BigDecimal amount = command.getExtractAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new LitemallBrokerageBelowMinimumException("Extract amount must be positive");
        }
        BrokerageService.BrokerageConfig config = brokerageService.loadConfig();
        if (amount.compareTo(config.minExtract()) < 0) {
            throw new LitemallBrokerageBelowMinimumException("Extract amount " + amount
                    + " is below the minimum of " + config.minExtract());
        }

        // Guarded debit first: overdraw (or a raced concurrent extract) is a clean
        // 0-row update and nothing else has been written yet.
        int debited = userMapper.debitBrokerage(command.getUserId(), amount);
        if (debited == 0) {
            throw new LitemallBrokerageInsufficientException("Insufficient brokerage balance for extract of " + amount);
        }
        BigDecimal balanceAfter = userMapper.selectBrokeragePriceByUserId(command.getUserId());
        if (balanceAfter == null) {
            balanceAfter = BigDecimal.ZERO;
        }

        LitemallUserId userId = new LitemallUserId(command.getUserId());
        LitemallMoney extractAmount = new LitemallMoney(amount);
        LitemallExtractAggregate extract = new LitemallExtractAggregate(
                userId,
                command.getRealName(),
                command.getExtractType(),
                command.getBankCode(),
                command.getBankAddress(),
                extractAmount,
                new LitemallMoney(balanceAfter)
        );
        extractRepository.add(extract);

        // pm=0 ledger entry: the audit trail of the debit AND the marker that this
        // extract is brokerage-sourced (litemall_user_extract has no source column).
        LocalDateTime now = LocalDateTime.now();
        LitemallUserBrokerageRecord record = new LitemallUserBrokerageRecord();
        record.setUserId(command.getUserId());
        record.setLinkId(String.valueOf(extract.getExtractId().getId()));
        record.setLinkType(LitemallUserBrokerageRecord.LINK_TYPE_EXTRACT);
        record.setPm(LitemallUserBrokerageRecord.PM_EXPENDITURE);
        record.setTitle("Commission withdrawal");
        record.setPrice(amount);
        record.setBalance(balanceAfter);
        record.setMark("Withdrawal request #" + extract.getExtractId().getId()
                + " via " + command.getExtractType());
        record.setStatus(LitemallUserBrokerageRecord.STATUS_VALID);
        record.setFreezeTime(null);
        record.setUnfreezeTime(null);
        record.setAddTime(now);
        record.setUpdateTime(now);
        record.setDeleted(false);
        brokerageRecordMapper.insert(record);

        if (extract.getExtractId() != null) {
            domainEventPublisher.publish(new LitemallExtractRequestedEvent(
                    extract.getExtractId(),
                    userId,
                    extractAmount
            ));
        }
        log.info("Brokerage extract requested for userId={}, amount={}, extractId={}",
                command.getUserId(), amount, extract.getExtractId().getId());
        return extract;
    }

    // ------------------------------------------------------------------
    // Admin extract console (Wave 5): /srv/private/admin/extract/**
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<LitemallUserExtract> adminList(Byte status, int page, int limit) {
        return extractMapper.selectAdminPage(status, Math.max(0, (page - 1) * limit), limit);
    }

    @Transactional(readOnly = true)
    public long adminCount(Byte status) {
        return extractMapper.countAdmin(status);
    }

    /** The extract's funding source, resolved from the pm=0 ledger marker row. */
    @Transactional(readOnly = true)
    public String sourceOf(Integer extractId) {
        return brokerageRecordMapper.selectExtractDebit(extractId) != null
                ? LitemallExtractRequestCommand.SOURCE_BROKERAGE
                : LitemallExtractRequestCommand.SOURCE_WALLET;
    }

    /**
     * Approve a PENDING extract (guarded {@code status 0 → 2}; the money was already
     * debited at request time, so approval is bookkeeping only).
     *
     * @throws IllegalStateException when the row is missing or not PENDING (→ 422)
     */
    public void approveExtract(Integer extractId) {
        int updated = extractMapper.approveFromPending(extractId);
        if (updated == 0) {
            throw new IllegalStateException("Extract " + extractId + " is not pending (already handled?)");
        }
        log.info("Extract {} approved", extractId);
    }

    /**
     * Reject a PENDING extract (guarded {@code status 0 → -1} + {@code fail_msg}/
     * {@code fail_time}) and refund the debited money to the balance it came from:
     * brokerage extracts (identified by their pm=0 ledger row) get
     * {@code creditBrokerage} and the ledger row flipped to INVALID; wallet extracts
     * get the standard wallet credit (with its bill entry).
     *
     * @throws IllegalStateException when the row is missing or not PENDING (→ 422)
     */
    public void rejectExtract(Integer extractId, String reason) {
        LitemallUserExtract extract = extractMapper.selectByPrimaryKey(extractId);
        if (extract == null) {
            throw new IllegalStateException("Extract " + extractId + " not found");
        }
        int updated = extractMapper.rejectFromPending(extractId,
                reason == null || reason.isBlank() ? "Rejected by admin" : reason);
        if (updated == 0) {
            throw new IllegalStateException("Extract " + extractId + " is not pending (already handled?)");
        }

        LitemallUserBrokerageRecord debitRecord = brokerageRecordMapper.selectExtractDebit(extractId);
        if (debitRecord != null) {
            // Brokerage-sourced: put the money back and invalidate the debit entry
            // (guarded — a 0-row flip only means the mark was already invalidated).
            int credited = userMapper.creditBrokerage(extract.getUserId(), extract.getExtractPrice());
            if (credited == 0) {
                throw new IllegalStateException("Refund failed: user " + extract.getUserId() + " not found");
            }
            brokerageRecordMapper.invalidateExtractDebit(debitRecord.getId());
            log.info("Extract {} rejected; refunded {} to brokerage balance of user {}",
                    extractId, extract.getExtractPrice(), extract.getUserId());
        } else {
            // Wallet-sourced: standard wallet credit, with its bill ledger entry.
            walletService.credit(new LitemallWalletCreditCommand(
                    extract.getUserId(),
                    extract.getExtractPrice(),
                    "Withdrawal rejected",
                    "extract",
                    extract.getExtractType(),
                    String.valueOf(extractId),
                    "Withdrawal request rejected: " + (reason == null ? "" : reason)
            ));
            log.info("Extract {} rejected; refunded {} to wallet of user {}",
                    extractId, extract.getExtractPrice(), extract.getUserId());
        }
    }
}
