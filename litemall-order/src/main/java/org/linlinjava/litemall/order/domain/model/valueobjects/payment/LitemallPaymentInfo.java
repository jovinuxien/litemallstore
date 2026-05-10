package org.linlinjava.litemall.order.domain.model.valueobjects.payment;


import com.stripe.model.PaymentMethod;
import lombok.Data;

@Data
public class LitemallPaymentInfo {

    private final LitemallPaymentAmount paymentMethod;
    private final String paymentGateway;
    private final LitemallPaymentAmount amount;
    private final PaymentCard cardInfo;
    private final DigitalWallet digitalWallet;
    private final String paymentIntentId;
    private final String customerId;
    private final Map<String, String> metadata;
    private final BillingAddress billingAddress;
    private final String returnUrl;
    private final String callbackUrl;

    private PaymentInfo(PaymentMethod paymentMethod, String paymentGateway,
                        PaymentAmount amount, PaymentCard cardInfo,
                        DigitalWallet digitalWallet, String paymentIntentId,
                        String customerId, Map<String, String> metadata,
                        BillingAddress billingAddress, String returnUrl,
                        String callbackUrl) {
        this.paymentMethod = paymentMethod;
        this.paymentGateway = paymentGateway;
        this.amount = amount;
        this.cardInfo = cardInfo;
        this.digitalWallet = digitalWallet;
        this.paymentIntentId = paymentIntentId;
        this.customerId = customerId;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.billingAddress = billingAddress;
        this.returnUrl = returnUrl;
        this.callbackUrl = callbackUrl;
    }

    public static class Builder {
        private PaymentMethod paymentMethod;
        private String paymentGateway;
        private PaymentAmount amount;
        private PaymentCard cardInfo;
        private DigitalWallet digitalWallet;
        private String paymentIntentId;
        private String customerId;
        private Map<String, String> metadata = new HashMap<>();
        private BillingAddress billingAddress;
        private String returnUrl;
        private String callbackUrl;

        public Builder paymentMethod(PaymentMethod paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        public Builder paymentGateway(String paymentGateway) {
            this.paymentGateway = paymentGateway;
            return this;
        }

        public Builder amount(PaymentAmount amount) {
            this.amount = amount;
            return this;
        }

        public Builder cardInfo(PaymentCard cardInfo) {
            this.cardInfo = cardInfo;
            return this;
        }

        public Builder digitalWallet(DigitalWallet digitalWallet) {
            this.digitalWallet = digitalWallet;
            return this;
        }

        public Builder paymentIntentId(String paymentIntentId) {
            this.paymentIntentId = paymentIntentId;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            return this;
        }

        public Builder addMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder billingAddress(BillingAddress billingAddress) {
            this.billingAddress = billingAddress;
            return this;
        }

        public Builder returnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        public Builder callbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
            return this;
        }

        public PaymentInfo build() {
            // Validation
            if (paymentMethod == null) {
                throw new IllegalArgumentException("Payment method is required");
            }
            if (paymentGateway == null || paymentGateway.trim().isEmpty()) {
                throw new IllegalArgumentException("Payment gateway is required");
            }
            if (amount == null) {
                throw new IllegalArgumentException("Payment amount is required");
            }

            return new PaymentInfo(paymentMethod, paymentGateway, amount, cardInfo,
                    digitalWallet, paymentIntentId, customerId, metadata,
                    billingAddress, returnUrl, callbackUrl);
        }
    }
}
