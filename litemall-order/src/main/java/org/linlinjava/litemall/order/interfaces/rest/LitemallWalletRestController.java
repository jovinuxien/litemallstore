package org.linlinjava.litemall.order.interfaces.rest;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.order.application.LitemallWalletOrchestratorService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallBillAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallExtractAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallRechargeAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallWalletAggregate;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallExtractRequestCommand;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallRechargeCreateCommand;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletCreditCommand;
import org.linlinjava.litemall.order.domain.model.commands.wallet.LitemallWalletDebitCommand;
import org.linlinjava.litemall.order.interfaces.dtos.wallet.BillDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.wallet.WalletBalanceDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.wallet.WalletOperationDtoResponse;
import org.linlinjava.litemall.order.interfaces.util.WalletHttpResponseUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Wallet self-service edge. The acting user is taken from the gateway-trusted
 * {@code X-User-Id} header (as in {@link LitemallOrderRestController}), never from
 * a request-supplied path/param — so a caller can only ever touch their own wallet
 * (closes the prior IDOR on these money-moving operations).
 *
 * <p>NOTE: {@code credit}/{@code debit} are privileged operations; the gateway must
 * route them only from internal/admin callers, not the customer SPA — tracked as a
 * gateway-admin follow-up.
 */
@RestController
@RequestMapping("/srv/wallet")
@Slf4j
public class LitemallWalletRestController {

    private final LitemallWalletOrchestratorService walletOrchestratorService;

    public LitemallWalletRestController(LitemallWalletOrchestratorService walletOrchestratorService) {
        this.walletOrchestratorService = walletOrchestratorService;
    }

    /**
     * Get the balance for a user's wallet.
     */
    @GetMapping("/balance")
    public ResponseEntity<WalletBalanceDtoResponse> getBalance(@RequestHeader("X-User-Id") Integer userId) {
        log.info("GET balance for userId={}", userId);
        LitemallWalletAggregate wallet = walletOrchestratorService.getWallet(userId);
        WalletBalanceDtoResponse response = WalletBalanceDtoResponse.of(
                userId,
                wallet.getBalance().getAmount(),
                wallet.getBrokerageBalance().getAmount()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Credit (add funds to) a user's wallet.
     */
    @PostMapping("/credit")
    public ResponseEntity<WalletOperationDtoResponse> credit(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody LitemallWalletCreditCommand command) {
        log.info("POST credit for userId={}, amount={}", userId, command.getAmount());
        command.setUserId(userId);

        try {
            LitemallWalletAggregate wallet = walletOrchestratorService.creditWallet(command);
            WalletOperationDtoResponse response = WalletOperationDtoResponse.success(
                    userId, "CREDIT", wallet.getBalance().getAmount(), "Wallet credited successfully");
            return WalletHttpResponseUtil.buildSuccessResponse(response, "CREDIT");
        } catch (IllegalArgumentException e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "CREDIT", e.getMessage());
            return WalletHttpResponseUtil.buildErrorResponse(errorResponse, "CREDIT", e);
        } catch (Exception e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "CREDIT", "Credit operation failed: " + e.getMessage());
            return WalletHttpResponseUtil.buildErrorResponse(errorResponse, "CREDIT", e);
        }
    }

    /**
     * Debit (deduct funds from) a user's wallet.
     */
    @PostMapping("/debit")
    public ResponseEntity<WalletOperationDtoResponse> debit(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody LitemallWalletDebitCommand command) {
        log.info("POST debit for userId={}, amount={}", userId, command.getAmount());
        command.setUserId(userId);

        try {
            LitemallWalletAggregate wallet = walletOrchestratorService.debitWallet(command);
            WalletOperationDtoResponse response = WalletOperationDtoResponse.success(
                    userId, "DEBIT", wallet.getBalance().getAmount(), "Wallet debited successfully");
            return WalletHttpResponseUtil.buildSuccessResponse(response, "DEBIT");
        } catch (IllegalStateException e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "DEBIT", e.getMessage());
            return WalletHttpResponseUtil.buildErrorResponse(errorResponse, "DEBIT", e);
        } catch (Exception e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "DEBIT", "Debit operation failed: " + e.getMessage());
            return WalletHttpResponseUtil.buildErrorResponse(errorResponse, "DEBIT", e);
        }
    }

    /**
     * Create a recharge (top-up) for a user's wallet.
     */
    @PostMapping("/recharge")
    public ResponseEntity<WalletOperationDtoResponse> createRecharge(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody LitemallRechargeCreateCommand command) {
        log.info("POST recharge for userId={}, amount={}", userId, command.getPrice());
        command.setUserId(userId);

        try {
            LitemallRechargeAggregate recharge = walletOrchestratorService.createRecharge(command);
            WalletOperationDtoResponse response = WalletOperationDtoResponse.success(
                    userId, "RECHARGE", null, "Recharge created successfully");
            return WalletHttpResponseUtil.buildSuccessResponse(response, "RECHARGE");
        } catch (Exception e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "RECHARGE", "Recharge creation failed: " + e.getMessage());
            return WalletHttpResponseUtil.buildErrorResponse(errorResponse, "RECHARGE", e);
        }
    }

    /**
     * Request a withdrawal (extract) from a user's wallet.
     */
    @PostMapping("/extract")
    public ResponseEntity<WalletOperationDtoResponse> requestExtract(
            @RequestHeader("X-User-Id") Integer userId,
            @RequestBody LitemallExtractRequestCommand command) {
        log.info("POST extract for userId={}, amount={}", userId, command.getExtractAmount());
        command.setUserId(userId);

        try {
            LitemallExtractAggregate extract = walletOrchestratorService.requestExtract(command);
            WalletOperationDtoResponse response = WalletOperationDtoResponse.success(
                    userId, "EXTRACT", extract.getBalanceAfter().getAmount(), "Extract request submitted successfully");
            return WalletHttpResponseUtil.buildSuccessResponse(response, "EXTRACT");
        } catch (IllegalStateException e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "EXTRACT", e.getMessage());
            return WalletHttpResponseUtil.buildErrorResponse(errorResponse, "EXTRACT", e);
        } catch (Exception e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "EXTRACT", "Extract request failed: " + e.getMessage());
            return WalletHttpResponseUtil.buildErrorResponse(errorResponse, "EXTRACT", e);
        }
    }

    /**
     * List all bills for a user.
     */
    @GetMapping("/bills")
    public ResponseEntity<List<BillDtoResponse>> listBills(@RequestHeader("X-User-Id") Integer userId) {
        log.info("GET bills for userId={}", userId);
        List<LitemallBillAggregate> bills = walletOrchestratorService.getBills(userId);
        List<BillDtoResponse> dtos = bills.stream()
                .map(BillDtoResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}
