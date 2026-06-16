package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.order.application.util.exception.order.LitemallOrderServiceException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCartRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCartId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LitemallCartServiceLayer {


    @Autowired
    private final LitemallCartRepository cartRepository;

    public LitemallCartServiceLayer(LitemallCartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    public List<LitemallCartAggregate> getCheckedCartItems(LitemallCartId cartId, LitemallUserId userId){

        if(cartId.getId() == 0){
            return cartRepository.findCheckedByUserId(userId);
        } else {
            List<LitemallCartAggregate> checkedCartItems = new ArrayList<>(0);
            LitemallCartAggregate checkedItems  = cartRepository.findById(cartId);
            checkedCartItems.add(checkedItems);
            return checkedCartItems;
        }
    }

    public List<LitemallCartAggregate> listAllCartItems(LitemallUserId userId) {
        return cartRepository.findAllActiveByUserId(userId);
    }

    public LitemallCartAggregate getCartItem(LitemallCartId cartId, LitemallUserId userId) {
        LitemallCartAggregate item = cartRepository.findActiveById(cartId);
        ensureOwnership(item, userId, cartId);
        return item;
    }

    public LitemallCartAggregate addCartItem(LitemallCartAggregate cart) {
        LitemallCartAggregate existing = cartRepository.findByUserIdAndGoodsId(
                cart.getUserId(), cart.getGoodsId(), cart.getProductId());
        if (existing != null) {
            existing.setNumber(existing.getNumber() + cart.getNumber());
            cartRepository.update(existing);
            return existing;
        }
        cartRepository.addNewCart(cart);
        return cart;
    }

    public LitemallCartAggregate updateCartItem(LitemallCartId cartId, LitemallUserId userId,
                                                Integer number, String[] specifications) {
        LitemallCartAggregate item = cartRepository.findActiveById(cartId);
        ensureOwnership(item, userId, cartId);
        if (number != null) {
            item.setNumber(number);
        }
        if (specifications != null) {
            item.setSpecifications(specifications);
        }
        cartRepository.update(item);
        return item;
    }

    public void removeCartItem(LitemallCartId cartId, LitemallUserId userId) {
        LitemallCartAggregate item = cartRepository.findActiveById(cartId);
        ensureOwnership(item, userId, cartId);
        cartRepository.deleteById(cartId);
    }

    public void clearCart(LitemallUserId userId) {
        cartRepository.clearAllByUserId(userId);
    }

    private static void ensureOwnership(LitemallCartAggregate item, LitemallUserId userId, LitemallCartId cartId) {
        if (item == null) {
            throw new LitemallOrderServiceException("Cart item not found: " + cartId.getId());
        }
        if (!item.getUserId().getId().equals(userId.getId())) {
            throw new LitemallOrderServiceException("Cart item " + cartId.getId() + " does not belong to user " + userId.getId());
        }
    }
}
