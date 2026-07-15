package org.linlinjava.litemall.db.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * V38: item-item co-occurrence row (litemall_goods_related). One row per goods;
 * {@code relatedIds} is a comma-separated goods-id list, strongest affinity
 * first, recomputed wholesale by goods-management's co-occurrence batch —
 * hand-maintained like every litemall-db domain class (never regenerate).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LitemallGoodsRelated {

    private Integer goodsId;
    private String relatedIds;
    private LocalDateTime updateTime;
}
