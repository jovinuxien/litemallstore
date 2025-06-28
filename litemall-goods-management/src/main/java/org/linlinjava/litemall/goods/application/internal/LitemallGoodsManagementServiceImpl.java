package org.linlinjava.litemall.goods.application.internal;

import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.db.service.LitemallCartService;
import org.linlinjava.litemall.goods.application.LitemallGoodsManagementService;
import org.linlinjava.litemall.goods.application.util.exception.goods.LitemallGoodsNotFoundException;
import org.linlinjava.litemall.goods.application.util.exception.goods.LitemallGoodsProductNotFoundException;
import org.linlinjava.litemall.goods.application.util.exception.goods.LitemallInsufficientStockException;
import org.linlinjava.litemall.goods.domain.model.agregates.*;
import org.linlinjava.litemall.goods.domain.model.repositories.*;
import org.linlinjava.litemall.goods.domain.model.util.dto.GoodsAllInOne;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsProductId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.goods.domain.model.valueobjects.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.manufacturer.LitemallManufacturerId;
import org.linlinjava.litemall.goods.infrastructure.messaging.source.SimpleSourceBean;
import org.linlinjava.litemall.goods.utils.ActionEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.linlinjava.litemall.goods.domain.model.util.dto.GoodsServiceResponseCode.GOODS_NAME_EXIST;

@Service
public class LitemallGoodsManagementServiceImpl  implements LitemallGoodsManagementService {


    private final LitemallGoodsRepository goodsRepository;
    private final LitemallCategoryRepository categoryRepository;
    private final LitemallBrandRepository brandRepository;

    private final LitemallGoodsProductRepository goodsProductRepository;
    private final LitemallGoodsAttributeRepository goodsAttributeRepository;
    private final LitemallGoodsSpecificationRepository goodsSpecificationRepository;
    //private final LitemallDomainEventPublisher eventPublisher;

    private final QCodeService qCodeService;
    private final LitemallCartService cartService;

    //@Autowired
    //private SimpleSourceBean simpleSourceBean;

 public LitemallGoodsManagementServiceImpl(LitemallGoodsRepository goodsRepository,
                                           LitemallCategoryRepository categoryRepository,
                                           LitemallBrandRepository brandRepository,
                                           LitemallGoodsProductRepository goodsProductRepository,
                                           LitemallGoodsAttributeRepository goodsAttributeRepository,
                                           LitemallGoodsSpecificationRepository goodsSpecificationRepository,
                                           QCodeService qCodeService,
                                           LitemallCartService cartService
                                           // LitemallDomainEventPublisher domainEventPublisher,
                                           // QCodeService qCodeService,
                                           // LitemallCartService cartService,
                                           // LitemallDomainEventPublisher domainEventPublisher,
 ) {
                                           //LitemallDomainEventPublisher eventPublisher, ) {
     //this.eventPublisher = eventPublisher;
     this.goodsRepository = goodsRepository;
     this.categoryRepository = categoryRepository;
     this.brandRepository = brandRepository;
     this.goodsProductRepository = goodsProductRepository;
     this.goodsAttributeRepository = goodsAttributeRepository;
     this.goodsSpecificationRepository = goodsSpecificationRepository;

     this.qCodeService = qCodeService;
     this.cartService = cartService;
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
    public void reduceStock(LitemallGoodsProductId productId, Short number) {
       goodsProductRepository.reduceStock(productId, number);
    }

    @Override
    public Object addAllGoods(List<GoodsAllInOne> allGoodsList) {
        return null;
    }

    @Override
    public Object goodsDetail(LitemallGoodsId goodsId) {

        LitemallGoodsAggregate goodsAggregate = goodsRepository.findById(goodsId);
        List<LitemallGoodsProductAggregate> products = goodsProductRepository.findByGoodsId(goodsId);
        List<LitemallGoodsSpecificationAggregate> specifications = goodsSpecificationRepository.findSpecificationByGoodsId(goodsId);
        List<LitemallGoodsAttributeAggregate> attributes = goodsAttributeRepository.queryByGoodsId(goodsId);

        LitemallCategoryId categoryId = goodsAggregate.getCategoryId();
        LitemallCategoryAggregate categoryAggregate = categoryRepository.findById(categoryId);
        List<Integer> categoryIds = new ArrayList<>();
        if (categoryAggregate != null) {
            Integer parentCategoryId = categoryAggregate.getParentId();
            categoryIds.add(parentCategoryId);
            categoryIds.add(categoryId.getId());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("goods", goodsAggregate);
        data.put("specifications", specifications);
        data.put("products", products);
        data.put("attributes", attributes);
        data.put("categoryIds", categoryIds);

        return ResponseUtil.ok(data);
    }

    @Override
    public Object addGoods(GoodsAllInOne goodsAllInOne) {

     Object error = validate(goodsAllInOne);
     if(error != null){
         return error;
     }

     LitemallGoodsAggregate goodsAggregate = goodsAllInOne.getGoods();
     List<LitemallGoodsProductAggregate> goodsProductAggregate = goodsAllInOne.getProducts();
     List<LitemallGoodsAttributeAggregate> goodsAttributeAggregates = goodsAllInOne.getAttributes();
     List<LitemallGoodsSpecificationAggregate> goodsSpecificationAggregates = goodsAllInOne.getSpecifications();

        String name = goodsAggregate.getGoodsName();
        if (goodsRepository.checkExistByName(name)) {
            return ResponseUtil.fail(GOODS_NAME_EXIST, "Product name already exists");
        }
        // There is a field retailPrice in the product
        // table that records the lowest price of the current product
        BigDecimal retailPrice = new BigDecimal(Integer.MAX_VALUE);
        for (LitemallGoodsProductAggregate product : goodsProductAggregate) {
            LitemallMoney productPrice = product.getPrice();
            if(retailPrice.compareTo(productPrice.getAmount()) == 1){
                retailPrice = productPrice.getAmount();
            }
        }
        goodsAggregate.setRetailPrice(new LitemallMoney(retailPrice));
        //Basic information table of goods litemall_goods
        goodsRepository.addGoods(goodsAggregate);

        //simpleSourceBean.publishGoodsChange(ActionEnum.CREATE, goodsAggregate.getGoodsId());
        String url = qCodeService.createGoodShareImage(goodsAggregate.getGoodsId().getId().toString(), goodsAggregate.getPicUrl(), goodsAggregate.getGoodsName());

        if (!StringUtils.isEmpty(url)) {
            goodsAggregate.setShareUrl(url);
            if (goodsRepository.updateById(goodsAggregate) == 0) {
                throw new RuntimeException("Failed to update data");
            }
        }

        // Product specification table litemall_goods_specification
        for (LitemallGoodsSpecificationAggregate specificationAggregate : goodsSpecificationAggregates) {
            specificationAggregate.setGoodsId(goodsAggregate.getGoodsId());
            goodsSpecificationRepository.add(specificationAggregate);
        }

        // 商品参数表litemall_goods_attribute
        for (LitemallGoodsAttributeAggregate attributeAggregate : goodsAttributeAggregates) {
            attributeAggregate.setGoodsId(goodsAggregate.getGoodsId());
            goodsAttributeRepository.save(attributeAggregate);
        }

        // 商品货品表litemall_product
        for (LitemallGoodsProductAggregate productAggregate : goodsProductAggregate) {
            productAggregate.setGoodsId(goodsAggregate.getGoodsId());
            goodsProductRepository.addGoodsProduct(productAggregate);
        }

        return ResponseUtil.ok();

    }

    @Override
    public Object updateGoods(GoodsAllInOne goodsAllInOne) {
        Object error = validate(goodsAllInOne);
        if (error != null) {
            return error;
        }

        LitemallGoodsAggregate goodsAggregate = goodsAllInOne.getGoods();
        List<LitemallGoodsAttributeAggregate> attributeAggregates = goodsAllInOne.getAttributes();
        List<LitemallGoodsSpecificationAggregate> specificationAggregates = goodsAllInOne.getSpecifications();
        List<LitemallGoodsProductAggregate> productAggregates = goodsAllInOne.getProducts();

        //将生成的分享图片地址写入数据库
        String url = qCodeService.createGoodShareImage(goodsAggregate.getGoodsId().toString(), goodsAggregate.getPicUrl(), goodsAggregate.getGoodsName());
        goodsAggregate.setShareUrl(url);

        // 商品表里面有一个字段retailPrice记录当前商品的最低价
        BigDecimal retailPrice = new BigDecimal(Integer.MAX_VALUE);
        for (LitemallGoodsProductAggregate product : productAggregates) {
            LitemallMoney productPrice = product.getPrice();
            if(retailPrice.compareTo(productPrice.getAmount()) == 1){
                retailPrice = productPrice.getAmount();
            }
        }
        goodsAggregate.setRetailPrice(new LitemallMoney(retailPrice));

        // 商品基本信息表litemall_goods
        if (goodsRepository.updateById(goodsAggregate) == 0) {
            throw new RuntimeException("Failed to update data");
        }

        LitemallGoodsId gid = goodsAggregate.getGoodsId();

        // 商品规格表litemall_goods_specification
        for (LitemallGoodsSpecificationAggregate specification : specificationAggregates) {
            // 目前只支持更新规格表的图片字段
            if(specification.getUpdateTime() == null){
                specification.setSpecifications(null);
                specification.setValue(null);
                goodsSpecificationRepository.updateById(specification);
            }
        }

        // 商品货品表litemall_product
        for (LitemallGoodsProductAggregate productAggregate : productAggregates) {
            if(productAggregate.getUpdateTime() == null) {
                goodsProductRepository.updateGoodsById(productAggregate);
            }
        }

        // 商品参数表litemall_goods_attribute
        for (LitemallGoodsAttributeAggregate attributeAggregate : attributeAggregates) {
            if (attributeAggregate.getGoodsId() == null || attributeAggregate.getGoodsId().getId().equals(0)){
                attributeAggregate.setGoodsId(goodsAggregate.getGoodsId());
                goodsAttributeRepository.updateById(attributeAggregate);
            }
            else if(attributeAggregate.isDeleted()){
                attributeAggregate.setDeleted(true);
            }
            else if(attributeAggregate.getUpdateTime() == null){
                goodsAttributeRepository.updateById(attributeAggregate);
            }
        }

        // 这里需要注意的是购物车litemall_cart有些字段是拷贝商品的一些字段，因此需要及时更新
        // 目前这些字段是goods_sn, goods_name, price, pic_url
        for (LitemallGoodsProductAggregate product : productAggregates) {
            cartService.updateProduct(product.getGoodsProductId().getId(), goodsAggregate.getGoodsSn(), goodsAggregate.getGoodsName(),
                    product.getPrice().getAmount(), product.getUrl());
        }

        return ResponseUtil.ok();
    }

    @Override
    public Object deleteGoods(LitemallGoodsAggregate goodsAggregate) {

     LitemallGoodsId goodsId = goodsAggregate.getGoodsId();
        if (goodsId == null) {
            return ResponseUtil.badArgument();
        }
        goodsRepository.deleteById(goodsId);
        goodsSpecificationRepository.removeByGoodsId(goodsId);
        goodsAttributeRepository.removeByGoodsId(goodsId);
        goodsProductRepository.deleteByGoodsId(goodsId);
        return ResponseUtil.ok();
    }

    @Override
    public Object getGoodsAggregateById(LitemallGoodsId goodsId) {
     Map<String, LitemallGoodsAggregate> result = new HashMap<>();
      LitemallGoodsAggregate goodsAggregate  = goodsRepository.findById(goodsId);
      if(goodsAggregate != null){
          result.put("goods", goodsAggregate);
      } else {
          result.put("goods", null);
      }
      return result;
    }

    @Override
    public Object getGoodsProductAggregateByGoodsId(LitemallGoodsId goodsId) {
     Map<String, List<LitemallGoodsProductAggregate>> result = new HashMap<>();

     List<LitemallGoodsProductAggregate> goodsProductAggregateList = goodsProductRepository.findByGoodsId(goodsId);
     if(goodsProductAggregateList!= null){
          result.put("goodsProduct", goodsProductAggregateList);
      } else {
          result.put("goodsProduct", null);
      }
     return result;
    }

    @Override
    public Object getGoodsAttributeAggregateByGoodsId(LitemallGoodsId goodsId) {
        Map<String, List<LitemallGoodsAttributeAggregate>> result = new HashMap<>();

        List<LitemallGoodsAttributeAggregate> goodsAttributeAggregate = goodsAttributeRepository.queryByGoodsId(goodsId);
        if(goodsAttributeAggregate!= null){
            result.put("goodsAttribute", goodsAttributeAggregate);
        } else {
            result.put("goodsAttribute", null);
        }
        return result;
    }

    @Override
    public Object getGoodsSpecificationAggregateByGoodsId(LitemallGoodsId goodsId) {
     Map<String, List<LitemallGoodsSpecificationAggregate>> result = new HashMap<>();

     List<LitemallGoodsSpecificationAggregate> goodsSpecificationAggregateList = goodsSpecificationRepository.findSpecificationByGoodsId(goodsId);

     if(goodsSpecificationAggregateList!= null){
          result.put("goodsSpecification", goodsSpecificationAggregateList);
      } else {
          result.put("goodsSpecification", null);
      }
     return result;
    }


    private Object validate(GoodsAllInOne goodsAllInOne){

       LitemallGoodsAggregate goodsAggregate = goodsAllInOne.getGoods();

       if(StringUtils.isEmpty(goodsAggregate.getGoodsName())){
           return ResponseUtil.badArgument();
       }
        if(StringUtils.isEmpty(goodsAggregate.getGoodsSn())){
            return ResponseUtil.badArgument();
        }
        LitemallCategoryId categoryId = goodsAggregate.getCategoryId();
        if(categoryId != null && categoryId.getId() != 0){
            if(categoryRepository.findById(categoryId) != null){
                ResponseUtil.badArgumentValue();
            }
        }

        LitemallManufacturerId manufacturerId = goodsAggregate.getManufacturerId();
        if(manufacturerId != null && manufacturerId.getId() != 0){
            if(brandRepository.findById(manufacturerId) != null){
                ResponseUtil.badArgumentValue();
            }
        }

        List<LitemallGoodsAttributeAggregate> attributeAggregates = goodsAllInOne.getAttributes();
        for(LitemallGoodsAttributeAggregate goodsAttributeAggregate: attributeAggregates){
            if(StringUtils.isEmpty(goodsAttributeAggregate.getAttributeName())){
                ResponseUtil.badArgumentValue();
            }

            if(StringUtils.isEmpty(goodsAttributeAggregate.getAttributeValue())){
                ResponseUtil.badArgumentValue();
            }
        }

        List<LitemallGoodsSpecificationAggregate> specificationAggregates = goodsAllInOne.getSpecifications();
        for(LitemallGoodsSpecificationAggregate goodsSpecificationAggregate: specificationAggregates){
            if(StringUtils.isEmpty(goodsSpecificationAggregate.getSpecifications())){
                ResponseUtil.badArgument();
            }
            if(StringUtils.isEmpty(goodsSpecificationAggregate.getValue())){
                ResponseUtil.badArgument();
            }
        }

        List<LitemallGoodsProductAggregate> goodsProductAggregates = goodsAllInOne.getProducts();

        for(LitemallGoodsProductAggregate goodsProductAggregate: goodsProductAggregates){
            Integer number = goodsProductAggregate.getNumber();
            if(number == null || number < 0){
                ResponseUtil.badArgument();
            }

            LitemallMoney price = goodsProductAggregate.getPrice();
            if (price == null) {
                return ResponseUtil.badArgument();
            }

            String[] productSpecifications = goodsProductAggregate.getSpecification();
            if (productSpecifications.length != specificationAggregates.size()){
                return ResponseUtil.badArgument();
            }
        }

        return null;
    }
}
