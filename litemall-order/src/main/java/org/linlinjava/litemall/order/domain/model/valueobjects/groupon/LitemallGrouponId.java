package org.linlinjava.litemall.order.domain.model.valueobjects.groupon;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;

@Getter
public class LitemallGrouponId {

    private final Integer id;
    public LitemallGrouponId(Integer id) {
        if(id == null || id <= 0) {
            throw new IllegalArgumentException("Groupon ID must be a positive integer.");
        }

        this.id = id;
    }
}
