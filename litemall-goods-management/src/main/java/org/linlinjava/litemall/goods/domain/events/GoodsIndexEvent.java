package org.linlinjava.litemall.goods.domain.events;

import java.io.Serializable;

/**
 * Internal event published over RabbitMQ when a goods record is created,
 * updated, or deleted. The consumer ({@code MessageConsumer}) drives the
 * incremental OCS index update.
 */
public class GoodsIndexEvent implements Serializable {

    public enum Action { UPSERT, DELETE }

    private Integer goodsId;
    private Action action;

    public GoodsIndexEvent() {
    }

    public GoodsIndexEvent(Integer goodsId, Action action) {
        this.goodsId = goodsId;
        this.action = action;
    }

    public Integer getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Integer goodsId) {
        this.goodsId = goodsId;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }
}
