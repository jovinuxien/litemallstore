package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvideo;

import lombok.Data;

/** Request body for CJ {@code product/queryVideosByProductId}. */
@Data
public class CJProductVideoRequest {
    private String productId;
}
