package org.linlinjava.litemall.order.interfaces.util;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.order.domain.service.order.LitemallOrderOperationResult;
import org.linlinjava.litemall.order.interfaces.dtos.order.OrderOperationDtoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Slf4j
public class LitemallHttpResponseUtil {


    public static ResponseEntity<OrderOperationDtoResponse> buildResponse(LitemallOrderOperationResult result) {
        OrderOperationDtoResponse response = OrderOperationDtoResponse.fromResult(result);

        // Logging pour le debugging
        if (result.isSuccess()) {
            log.info("Order operation successful: {} - {}",
                    result.getOperationType(), result.getMessage());
        } else {
            log.warn("Order operation failed: {} - {}",
                    result.getOperationType(), result.getMessage());
        }

        // Mapping des statuts HTTP appropriés
        return buildHttpResponse(result, response);
    }

    public static ResponseEntity<OrderOperationDtoResponse> buildHttpResponse(
            LitemallOrderOperationResult result,
            OrderOperationDtoResponse response) {

        if (result.isSuccess()) {
            return buildSuccessResponse(result, response);
        } else {
            return buildErrorResponse(result, response);
        }
    }

    private static ResponseEntity<OrderOperationDtoResponse> buildSuccessResponse(
            LitemallOrderOperationResult result,
            OrderOperationDtoResponse response) {

        // Différents codes HTTP pour différents types de succès
        switch (result.getOperationType()) {
            case SUBMIT:
                // Order created - 201 Created
                return ResponseEntity.status(HttpStatus.CREATED).body(response);

            case PAY:
                // Payment processed - 200 OK avec en-tête supplémentaire
                return ResponseEntity.ok()
                        .header("X-Payment-Status", "processed")
                        .body(response);

            case CANCEL:
                // Order cancelled - 200 OK (certaines APIs utilisent 202 Accepted)
                return ResponseEntity.ok(response);

            case COMPLETE:
                // Order completed - 200 OK
                return ResponseEntity.ok(response);

            default:
                // Autres opérations réussies - 200 OK
                return ResponseEntity.ok(response);
        }
    }

    private static ResponseEntity<OrderOperationDtoResponse> buildErrorResponse(
            LitemallOrderOperationResult result,
            OrderOperationDtoResponse response) {

        // Codes HTTP spécifiques selon le type d'erreur
        switch (result.getOperationType()) {
            case SUBMIT:
                return handleSubmitErrors(result, response);

            case PAY:
                return handlePaymentErrors(result, response);

            case CANCEL:
                return handleCancellationErrors(result, response);

            case REFUND:
                return handleRefundErrors(result, response);

            default:
                return handleGenericErrors(result, response);
        }
    }

    private static ResponseEntity<OrderOperationDtoResponse> handleSubmitErrors(
            LitemallOrderOperationResult result,
            OrderOperationDtoResponse response) {

        // Erreurs spécifiques à la création de commande
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    private static ResponseEntity<OrderOperationDtoResponse> handlePaymentErrors(
            LitemallOrderOperationResult result,
            OrderOperationDtoResponse response) {

        // Erreurs de paiement - souvent liées à des problèmes de traitement
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
    }

    private static ResponseEntity<OrderOperationDtoResponse> handleCancellationErrors(
            LitemallOrderOperationResult result,
            OrderOperationDtoResponse response) {

        // Erreurs d'annulation - souvent des conflits de statut
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    private static ResponseEntity<OrderOperationDtoResponse> handleRefundErrors(
            LitemallOrderOperationResult result,
            OrderOperationDtoResponse response) {

        // Erreurs de remboursement
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    private static ResponseEntity<OrderOperationDtoResponse> handleGenericErrors(
            LitemallOrderOperationResult result,
            OrderOperationDtoResponse response) {

        // Erreurs génériques
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
