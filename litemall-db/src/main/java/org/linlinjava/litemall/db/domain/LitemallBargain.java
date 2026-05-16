package org.linlinjava.litemall.db.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LitemallBargain {

    public static final Boolean IS_DELETED  = Deleted.IS_DELETED.value();
    public static final Boolean NOT_DELETED = Deleted.NOT_DELETED.value();

    private Integer       id;
    /** 关联商品ID */
    private Integer       goodsId;
    /** 砍价活动名称 */
    private String        title;
    /** 活动图片 */
    private String        picUrl;
    /** 单位名称 */
    private String        unit;
    /** 库存 */
    private Integer       stock;
    /** 销量 */
    private Integer       sales;
    /** 原始价格 */
    private BigDecimal    price;
    /** 砍至最低价 */
    private BigDecimal    minPrice;
    /** 每用户发起砍价次数限制 */
    private Integer       num;
    /** 每次砍价最大金额 */
    private BigDecimal    bargainMaxPrice;
    /** 每次砍价最小金额 */
    private BigDecimal    bargainMinPrice;
    /** 帮砍次数(达到可购买) */
    private Integer       bargainNum;
    /** 砍价成功所需人数 */
    private Integer       peopleNum;
    /** 限购总数 0不限 */
    private Integer       quota;
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
    /** 活动开始时间 */
    private LocalDateTime startTime;
    /** 活动结束时间 */
    private LocalDateTime stopTime;
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
