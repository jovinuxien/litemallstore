package org.linlinjava.litemall.order.domain.model.util;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValidationResult {

    private final boolean valid;
    private final String errorMessage;


    public ValidationResult(boolean valid) {
        this.valid = valid;
        this.errorMessage = null;
    }
    public ValidationResult(boolean valid, String errorMessage) {
        this.valid = valid;
        this.errorMessage = errorMessage;
    }


    public static ValidationResult valid() {
        return new ValidationResult(true);
    }

    public static ValidationResult invalid(String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }
}
