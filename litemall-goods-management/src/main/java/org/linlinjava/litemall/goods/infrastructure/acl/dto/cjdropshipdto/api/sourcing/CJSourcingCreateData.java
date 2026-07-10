package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing;

import lombok.Data;

@Data
public class CJSourcingCreateData {
    private String cjSourcingId; // CJ's sourcing id — the product/sourcing/query key
    private String result;       // e.g. "success"
}
