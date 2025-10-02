package org.linlinjava.litemall.order.application.internal;

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


}
