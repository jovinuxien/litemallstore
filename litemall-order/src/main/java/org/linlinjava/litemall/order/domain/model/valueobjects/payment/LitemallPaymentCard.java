package org.linlinjava.litemall.order.domain.model.valueobjects.payment;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import lombok.Getter;

@Getter
public class LitemallPaymentCard {

    private final String cardNumber;
    private final String cardHolderName;
    private final String expiryMonth;
    private final String expiryYear;
    private final String cvv;
    private final CardType cardType;

    public LitemallPaymentCard(String cardNumber, String cardHolderName, String expiryMonth,
                       String expiryYear, String cvv) {
        this.cardNumber = maskCardNumber(cardNumber);
        this.cardHolderName = cardHolderName;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.cvv = cvv; // Note: In production, never store CVV
        this.cardType = determineCardType(cardNumber);
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return cardNumber;
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }

    private CardType determineCardType(String cardNumber) {
        if (cardNumber == null) return CardType.UNKNOWN;

        if (cardNumber.startsWith("4")) return CardType.VISA;
        if (cardNumber.startsWith("5")) return CardType.MASTERCARD;
        if (cardNumber.startsWith("34") || cardNumber.startsWith("37")) return CardType.AMEX;
        if (cardNumber.startsWith("6")) return CardType.DISCOVER;

        return CardType.UNKNOWN;
    }

    public enum CardType {
        VISA, MASTERCARD, AMEX, DISCOVER, UNKNOWN
    }
}
