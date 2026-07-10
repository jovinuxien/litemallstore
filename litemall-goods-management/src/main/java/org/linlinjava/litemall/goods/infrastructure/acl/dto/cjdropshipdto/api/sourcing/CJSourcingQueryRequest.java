package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing;

import lombok.Data;

import java.util.List;

/** Request body for CJ {@code product/sourcing/query}. */
@Data
public class CJSourcingQueryRequest {
    private List<String> sourceIds;
}
