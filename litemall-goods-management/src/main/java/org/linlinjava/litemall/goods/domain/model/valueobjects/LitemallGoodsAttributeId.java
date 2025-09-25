package org.linlinjava.litemall.goods.domain.model.valueobjects;

import lombok.Getter;
import org.springframework.web.bind.annotation.GetMapping;

@Getter
public class LitemallGoodsAttributeId {

    private final String id;

    public LitemallGoodsAttributeId(String id) {
        if(id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Goods ID must be a positive integer.");
        }
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
