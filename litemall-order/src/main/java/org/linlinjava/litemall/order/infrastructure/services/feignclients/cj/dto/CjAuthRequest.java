package org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CJ Dropshipping {@code getAccessToken} request body. {@code password} carries the CJ API key.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CjAuthRequest {
    private String email;
    private String password;
}
