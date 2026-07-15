package org.linlinjava.litemall.db.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LitemallSeckill {

    public static final Boolean IS_DELETED  = Deleted.IS_DELETED.value();
    public static final Boolean NOT_DELETED = Deleted.NOT_DELETED.value();

    private Integer       id;
    /** 关联商品ID */
    private Integer       goodsId;
    /** 商品名称 */
    private String        goodsName;
    /** 商品图片 */
    private String        picUrl;
    /** 秒杀价格 */
    private BigDecimal    price;
    /** 成本价 */
    private BigDecimal    cost;
    /** 秒杀库存 */
    private Integer       stock;
    /** 已售数量 */
    private Integer       sales;
    /** 限购总数 0不限 */
    private Integer       quota;
    /** 页面显示限购数 */
    private Integer       quotaShow;
    /** 秒杀时段(小时) */
    private Byte          time;
    /** 是否包邮 */
    private Boolean       isPostage;
    /** 邮费 */
    private BigDecimal    postage;
    /** 运费模板ID */
    private Integer       tempId;
    /** 重量kg */
    private BigDecimal    weight;
    /** 体积m³ */
    private BigDecimal    volume;
    /** 排序 */
    private Integer       sort;
    /** 状态 0下架 1上架 */
    private Byte          status;
    /** 是否删除 */
    private Boolean       isDel;
    /** 秒杀开始时间 */
    private LocalDateTime startTime;
    /** 秒杀结束时间 */
    private LocalDateTime stopTime;
    /** V38: goods.retail_price captured at swap-on; restored at swap-off. */
    private BigDecimal    originalRetailPrice;
    /** V38: true while the deal price is live on the goods row (the "deal is live" source of truth). */
    private Boolean       priceSwapped;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean       deleted;

    public void andLogicalDeleted(boolean deleted) {
        setDeleted(deleted ? Deleted.IS_DELETED.value() : Deleted.NOT_DELETED.value());
    }

    public enum Deleted {
        NOT_DELETED(Boolean.FALSE, "未删除"),
        IS_DELETED(Boolean.TRUE, "已删除");

        private final Boolean value;
        private final String  name;

        Deleted(Boolean value, String name) {
            this.value = value;
            this.name  = name;
        }

        public Boolean value() {
            return this.value;
        }

        public Boolean getValue() {
            return this.value;
        }

        public String getName() {
            return this.name;
        }
    }
}