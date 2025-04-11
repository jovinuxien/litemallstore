package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.db.domain.LitemallCart;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCartId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;

import java.util.List;

public interface LitemallCartRepository {

    List<LitemallCart> findCheckedByUserId(LitemallUserId userId);

    boolean isGoodsInAlreadyInCart(LitemallUserId userId, LitemallGoodsId goodsId, LitemallGoodsProductId productId);

    void add(LitemallCart cart);

    List<LitemallCart> findByUserId(LitemallUserId userId, LitemallCartId cartId);

    LitemallCart findById(LitemallCartId id);

    void clearCheckedByUserId(LitemallUserId userId);

    void deleteById(LitemallCartId id);

    List<LitemallCart> findByUserIdAndGoodsId(LitemallUserId userId, LitemallGoodsId goodsId);
}
