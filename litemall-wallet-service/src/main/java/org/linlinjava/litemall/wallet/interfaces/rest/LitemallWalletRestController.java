package org.linlinjava.litemall.wallet.interfaces.rest;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.wallet.application.LitemallWalletOrchestratorService;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallBillAggregate;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallExtractAggregate;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallRechargeAggregate;
import org.linlinjava.litemall.wallet.domain.model.agregates.LitemallWalletAggregate;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallExtractRequestCommand;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallRechargeCreateCommand;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallWalletCreditCommand;
import org.linlinjava.litemall.wallet.domain.model.commands.LitemallWalletDebitCommand;
import org.linlinjava.litemall.wallet.interfaces.dtos.wallet.BillDtoResponse;
import org.linlinjava.litemall.wallet.interfaces.dtos.wallet.WalletBalanceDtoResponse;
import org.linlinjava.litemall.wallet.interfaces.dtos.wallet.WalletOperationDtoResponse;
import org.linlinjava.litemall.wallet.interfaces.util.LitemallHttpResponseUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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
    @GetMapping("/{userId}/balance")
    public ResponseEntity<WalletBalanceDtoResponse> getBalance(@PathVariable Integer userId) {
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
    @PostMapping("/{userId}/credit")
    public ResponseEntity<WalletOperationDtoResponse> credit(
            @PathVariable Integer userId,
            @RequestBody LitemallWalletCreditCommand command) {
        log.info("POST credit for userId={}, amount={}", userId, command.getAmount());
        command.setUserId(userId);

        try {
            LitemallWalletAggregate wallet = walletOrchestratorService.creditWallet(command);
            WalletOperationDtoResponse response = WalletOperationDtoResponse.success(
                    userId, "CREDIT", wallet.getBalance().getAmount(), "Wallet credited successfully");
            return LitemallHttpResponseUtil.buildSuccessResponse(response, "CREDIT");
        } catch (IllegalArgumentException e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "CREDIT", e.getMessage());
            return LitemallHttpResponseUtil.buildErrorResponse(errorResponse, "CREDIT", e);
        } catch (Exception e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "CREDIT", "Credit operation failed: " + e.getMessage());
            return LitemallHttpResponseUtil.buildErrorResponse(errorResponse, "CREDIT", e);
        }
    }

    /**
     * Debit (deduct funds from) a user's wallet.
     */
    @PostMapping("/{userId}/debit")
    public ResponseEntity<WalletOperationDtoResponse> debit(
            @PathVariable Integer userId,
            @RequestBody LitemallWalletDebitCommand command) {
        log.info("POST debit for userId={}, amount={}", userId, command.getAmount());
        command.setUserId(userId);

        try {
            LitemallWalletAggregate wallet = walletOrchestratorService.debitWallet(command);
            WalletOperationDtoResponse response = WalletOperationDtoResponse.success(
                    userId, "DEBIT", wallet.getBalance().getAmount(), "Wallet debited successfully");
            return LitemallHttpResponseUtil.buildSuccessResponse(response, "DEBIT");
        } catch (IllegalStateException e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "DEBIT", e.getMessage());
            return LitemallHttpResponseUtil.buildErrorResponse(errorResponse, "DEBIT", e);
        } catch (Exception e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "DEBIT", "Debit operation failed: " + e.getMessage());
            return LitemallHttpResponseUtil.buildErrorResponse(errorResponse, "DEBIT", e);
        }
    }

    /**
     * Create a recharge (top-up) for a user's wallet.
     */
    @PostMapping("/{userId}/recharge")
    public ResponseEntity<WalletOperationDtoResponse> createRecharge(
            @PathVariable Integer userId,
            @RequestBody LitemallRechargeCreateCommand command) {
        log.info("POST recharge for userId={}, amount={}", userId, command.getPrice());
        command.setUserId(userId);

        try {
            LitemallRechargeAggregate recharge = walletOrchestratorService.createRecharge(command);
            WalletOperationDtoResponse response = WalletOperationDtoResponse.success(
                    userId, "RECHARGE", null, "Recharge created successfully");
            return LitemallHttpResponseUtil.buildSuccessResponse(response, "RECHARGE");
        } catch (Exception e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "RECHARGE", "Recharge creation failed: " + e.getMessage());
            return LitemallHttpResponseUtil.buildErrorResponse(errorResponse, "RECHARGE", e);
        }
    }

    /**
     * Request a withdrawal (extract) from a user's wallet.
     */
    @PostMapping("/{userId}/extract")
    public ResponseEntity<WalletOperationDtoResponse> requestExtract(
            @PathVariable Integer userId,
            @RequestBody LitemallExtractRequestCommand command) {
        log.info("POST extract for userId={}, amount={}", userId, command.getExtractAmount());
        command.setUserId(userId);

        try {
            LitemallExtractAggregate extract = walletOrchestratorService.requestExtract(command);
            WalletOperationDtoResponse response = WalletOperationDtoResponse.success(
                    userId, "EXTRACT", extract.getBalanceAfter().getAmount(), "Extract request submitted successfully");
            return LitemallHttpResponseUtil.buildSuccessResponse(response, "EXTRACT");
        } catch (IllegalStateException e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "EXTRACT", e.getMessage());
            return LitemallHttpResponseUtil.buildErrorResponse(errorResponse, "EXTRACT", e);
        } catch (Exception e) {
            WalletOperationDtoResponse errorResponse = WalletOperationDtoResponse.failure(userId, "EXTRACT", "Extract request failed: " + e.getMessage());
            return LitemallHttpResponseUtil.buildErrorResponse(errorResponse, "EXTRACT", e);
        }
    }

    /**
     * List all bills for a user.
     */
    @GetMapping("/{userId}/bills")
    public ResponseEntity<List<BillDtoResponse>> listBills(@PathVariable Integer userId) {
        log.info("GET bills for userId={}", userId);
        List<LitemallBillAggregate> bills = walletOrchestratorService.getBills(userId);
        List<BillDtoResponse> dtos = bills.stream()
                .map(BillDtoResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}
