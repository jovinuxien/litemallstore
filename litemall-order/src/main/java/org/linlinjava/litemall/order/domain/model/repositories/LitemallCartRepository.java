package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCartId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;

import java.util.List;

public interface LitemallCartRepository {

    List<LitemallCartAggregate> findCheckedByUserId(LitemallUserId userId);

    boolean isGoodsInAlreadyInCart(LitemallUserId userId, LitemallGoodsId goodsId, LitemallGoodsProductId productId);

    void addNewCart(LitemallCartAggregate cart);

    List<LitemallCartAggregate> findByUserId(LitemallUserId userId);

    LitemallCartAggregate findById(LitemallCartId id);

    void clearCheckedByUserId(LitemallUserId userId);

    int  updateCheck(LitemallUserId userId, List<LitemallGoodsProductId> productIdList, boolean checked);

    void deleteById(LitemallCartId id);

    LitemallCartAggregate findByUserIdAndGoodsId(LitemallUserId userId, LitemallGoodsId goodsId, LitemallGoodsProductId productId);
}
