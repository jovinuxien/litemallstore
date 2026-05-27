package org.linlinjava.litemall.goods.infrastructure.messaging;

import java.io.Serializable;

/**
 * Lightweight pointer published when a goods record is created, updated, or
 * deleted. The {@link MessageConsumer} on the indexer side reads it and
 * upserts/deletes the matching document in OCS. The payload deliberately
 * does not carry the goods snapshot — consumers re-read from the DB so they
 * always index the post-commit state.
 */
public class GoodsChangeMessage implements Serializable {

    public enum Action { UPSERT, DELETE }

    private Action action;
    private Integer goodsId;

    public GoodsChangeMessage() {}

    public GoodsChangeMessage(Action action, Integer goodsId) {
        this.action = action;
        this.goodsId = goodsId;
    }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    public Integer getGoodsId() { return goodsId; }
    public void setGoodsId(Integer goodsId) { this.goodsId = goodsId; }
}
