package org.linlinjava.litemall.goods.application.internal;

import org.linlinjava.litemall.goods.application.LitemallGoodsManagementService;
import org.linlinjava.litemall.goods.application.util.exception.goods.LitemallGoodsNotFoundException;
import org.linlinjava.litemall.goods.application.util.exception.goods.LitemallGoodsProductNotFoundException;
import org.linlinjava.litemall.goods.application.util.exception.goods.LitemallInsufficientStockException;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.goods.domain.model.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallGoodsProductRepository;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallGoodsRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsProductId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LitemallGoodsManagementServiceImpl  implements LitemallGoodsManagementService {
 private final LitemallGoodsRepository goodsRepository;
 private final LitemallDomainEventPublisher eventPublisher;
 private final LitemallGoodsProductRepository goodsProductRepository;

 public LitemallGoodsManagementServiceImpl(LitemallGoodsRepository goodsRepository,
                                           LitemallDomainEventPublisher eventPublisher, LitemallGoodsProductRepository goodsProductRepository) {
        this.goodsRepository = goodsRepository;
        this.eventPublisher = eventPublisher;
        this.goodsProductRepository = goodsProductRepository;
    }

    @Override
    public void verifyGoodsAvailability(List<LitemallGoodsId> goodsIds) {
        List<LitemallGoodsAggregate> goodsAggregates = goodsRepository.queryByIds(goodsIds);
        Short numberLimitInStock = 5; // TODO: Configurable
        if(goodsAggregates.size() != goodsIds.size()){
            throw new LitemallGoodsNotFoundException("Goods not found: " + goodsIds);
        }

        List<LitemallGoodsProductAggregate> existingProductAggregate = null;
        for(LitemallGoodsId ltmGoodsId : goodsIds){
            existingProductAggregate = goodsProductRepository.findByGoodsId(ltmGoodsId);

            if(existingProductAggregate.isEmpty()){
                throw new LitemallGoodsProductNotFoundException("The product for this goods is  not found: " + ltmGoodsId);
            }

            //This method makes multiple database calls which can leads to performance issues(N+1 problem).
           /* for(LitemallGoodsProductAggregate productAggregate : existingProductAggregate){
                if(!goodsProductRepository.isStockEnough(productAggregate.getGoodsProductId(), (short) numberLimitInStock )){
                    throw new LitemallInsufficientStockException("Not enough stock for goods: " + ltmGoodsId);
                }
            }*/


            // This in memory call via Stream API check is good compared to the database call.
            if(existingProductAggregate.stream().anyMatch(productAggregate -> productAggregate.isStockEnough(numberLimitInStock))){
                throw new LitemallInsufficientStockException("Not enough stock for goods: " + ltmGoodsId);
            }
        }

 }

    @Override
    public void reduceStock(Map<LitemallGoodsId, Integer> productStockMap) {

    }
}
