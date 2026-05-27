package org.linlinjava.litemall.goods.infrastructure.services.impl;

import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.service.LitemallCartService;
import org.linlinjava.litemall.goods.domain.model.aggregates.*;
import org.linlinjava.litemall.goods.domain.model.dto.goods.GoodsAllInOne;
import org.linlinjava.litemall.goods.domain.model.repositories.*;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallMoney;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.manufacturer.LitemallManufacturerId;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallBrandServiceApi;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallCatalogService;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallGoodsServiceApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.linlinjava.litemall.goods.domain.model.dto.goods.GoodsServiceResponseCode.GOODS_NAME_EXIST;

@Service
public class LitemallGoodsServiceApiImpl implements LitemallGoodsServiceApi {

    private final LitemallGoodsRepository goodsRepository;
    private final LitemallGoodsProductRepository goodsProductRepository;
    private final LitemallGoodsSpecificationRepository goodsSpecificationRepository;
    private final LitemallGoodsAttributeRepository goodsAttributeRepository;


    private final LitemallCatalogRepository categoryRepository;
    private final LitemallBrandRepository brandRepository;

    @Autowired
    private LitemallBrandServiceApi brandService;
    @Autowired
    private LitemallCatalogService catalogService;
    @Autowired
    private QCodeService qCodeService;
    @Autowired
    private LitemallCartService cartService;



    public LitemallGoodsServiceApiImpl(LitemallGoodsRepository goodsRepository,
                                       LitemallGoodsProductRepository goodsProductRepository,
                                       LitemallGoodsSpecificationRepository goodsSpecificationRepository,
                                       LitemallGoodsAttributeRepository goodsAttributeRepository,
                                       LitemallCatalogRepository categoryRepository,
                                       LitemallBrandRepository brandRepository) {
        this.goodsRepository = goodsRepository;
        this.goodsProductRepository = goodsProductRepository;
        this.goodsSpecificationRepository = goodsSpecificationRepository;
        this.goodsAttributeRepository = goodsAttributeRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @Override
    public List<LitemallGoodsAggregate> getGoodsByCategoryId(LitemallCategoryId categoryId) {
        return goodsRepository.queryByCategory(categoryId, 0, 100);
    }

    @Override
    public List<LitemallGoodsAggregate> getGoodsByCategoryId(LitemallCategoryId categoryId, Integer offset, Integer limit) {
        return goodsRepository.queryByCategory(categoryId, offset, limit);
    }

    @Override
    public int getGoodsOnSale() {
        return goodsRepository.queryOnSale();
    }

    @Override
    public List<LitemallGoodsAggregate> getGoodsByBrand(LitemallManufacturerId brandId, String keywords, Boolean isHot, Boolean isNew, Integer page, Integer size, String sort, String order) {
        return goodsRepository.queryByManufacturer(brandId, page * size, size);
    }

    @Override
    public LitemallGoodsAggregate getGoodsById(LitemallGoodsId goodsId) {
        return goodsRepository.findById(goodsId);
    }

    @Override
    public List<LitemallGoodsAggregate> getAllGoodByIds(List<LitemallGoodsId> ids) {
        return goodsRepository.queryByIds(ids);
    }

    @Override
    public List<LitemallGoodsAggregate> getGoodsByHot(int offset, int limit) {
        return goodsRepository.queryByHot(offset, limit);
    }

    @Override
    public List<LitemallGoodsAggregate> getGoodsByNew(int offset, int limit) {
        return goodsRepository.queryByNew(offset, limit);
    }

    @Override
    public List<LitemallGoodsAggregate> getGoodsBySelective(LitemallCategoryId catId, LitemallManufacturerId brandId, String keywords, Boolean isHot, Boolean isNew, Integer page, Integer size, String sort) {
        return goodsRepository.querySelective(catId, brandId, keywords, isHot, isNew, page, size, sort);
    }

    @Override
    public List<Integer> getCatIds(Integer brandId, String keywords, Boolean isHot, Boolean isNew) {
        return goodsRepository.getCatsId(brandId, keywords, isHot, isNew);
    }

    @Override
    public Object addAllGoods(List<GoodsAllInOne> allGoodsList) {
        return null;
    }

    @Override
    public Map<String, Object> getGoodsDetail(LitemallGoodsId goodsId, ThreadPoolExecutor executor, RejectedExecutionHandler handler, ArrayBlockingQueue<Runnable> queue) {

        //LitemallGoodsAggregate goodsAggregate = goodsRepository.findById(goodsId);
        LitemallGoodsAggregate goodsAggregate = this.getGoodsById(goodsId);

        Callable<List> goodsProductCallable = () -> goodsProductRepository.findByGoodsId(goodsId);
        //List<LitemallGoodsProductAggregate> products = goodsProductRepository.findByGoodsId(goodsId);
        Callable<List> goodsSpecificationCallable = () -> goodsSpecificationRepository.findSpecificationByGoodsId(goodsId);
        //List<LitemallGoodsSpecificationAggregate> specifications = goodsSpecificationRepository.findSpecificationByGoodsId(goodsId);
        Callable<List> goodsAttributeCallable  = () -> goodsAttributeRepository.queryByGoodsId(goodsId);
        //List<LitemallGoodsAttributeAggregate> attributes = goodsAttributeRepository.queryByGoodsId(goodsId);

        FutureTask<List> goodsProductTask = new FutureTask<>(goodsProductCallable);
        FutureTask<List>  goodsSpecificationTask = new FutureTask<>(goodsSpecificationCallable);
        FutureTask<List>  goodsAttributeTask = new FutureTask<>(goodsAttributeCallable);

        executor.submit(goodsProductTask);
        executor.submit(goodsSpecificationTask);
        executor.submit(goodsAttributeTask);

        LitemallCategoryId categoryId = goodsAggregate.getCategoryId();
        LitemallCategoryAggregate categoryAggregate = categoryRepository.findById(categoryId);
        List<Integer> categoryIds = new ArrayList<>();
        if (categoryAggregate != null) {
            Integer parentCategoryId = categoryAggregate.getParentId();
            categoryIds.add(parentCategoryId);
            categoryIds.add(Integer.valueOf(categoryId.getId()));
        }

        Map<String, Object> data = new HashMap<>();
        try {
            data.put("goods", goodsAggregate);
            data.put("specifications", goodsSpecificationTask.get());
            data.put("products", goodsProductTask.get());
            data.put("attributes", goodsAttributeTask.get());
            data.put("categoryIds", categoryIds);

        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        } catch (ExecutionException ee) {
            throw new RuntimeException(ee.getCause());
        }

        return data;
    }

    @Override
    public Object addGoods(GoodsAllInOne goodsAllInOne) {
        Object error = validate(goodsAllInOne);
        if (error != null) {
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
        goodsAggregate.setAddTime(LocalDateTime.now());
        goodsAggregate.setUpdateTime(LocalDateTime.now());
        goodsRepository.insertGoods(goodsAggregate);

        //simpleSourceBean.publishGoodsChange(ActionEnum.CREATE, goodsAggregate.getGoodsId());
        String url = qCodeService.createGoodShareImage(goodsAggregate.getGoodsId().getId().toString(), goodsAggregate.getPicUrl(), goodsAggregate.getGoodsName());
        //if (!StringUtils.hasLength(url)) {
        if (StringUtils.hasLength(url)) {
            goodsAggregate.setShareUrl(url);
            //if (goodsMapper.updateById(goodsAggregate) == 0) {
            if (goodsRepository.updateById(goodsAggregate) == 0) {
                throw new RuntimeException("Failed to update data");
            }
        }
        for (LitemallGoodsSpecificationAggregate specificationAggregate : goodsSpecificationAggregates) {
            specificationAggregate.setGoodsId(goodsAggregate.getGoodsId());
            goodsSpecificationRepository.add(specificationAggregate);
        }
        for (LitemallGoodsAttributeAggregate attributeAggregate : goodsAttributeAggregates) {
            attributeAggregate.setGoodsId(goodsAggregate.getGoodsId());
            goodsAttributeRepository.save(attributeAggregate);
        }
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

        String url = qCodeService.createGoodShareImage(goodsAggregate.getGoodsId().toString(), goodsAggregate.getPicUrl(), goodsAggregate.getGoodsName());
        goodsAggregate.setShareUrl(url);

        BigDecimal retailPrice = new BigDecimal(Integer.MAX_VALUE);
        for (LitemallGoodsProductAggregate product : productAggregates) {
            LitemallMoney productPrice = product.getPrice();
            if(retailPrice.compareTo(productPrice.getAmount()) == 1){
                retailPrice = productPrice.getAmount();
            }
        }
        goodsAggregate.setRetailPrice(new LitemallMoney(retailPrice));

        if (goodsRepository.updateById(goodsAggregate) == 0) {
            throw new RuntimeException("Failed to update data");
        }

        LitemallGoodsId gid = goodsAggregate.getGoodsId();
        for (LitemallGoodsSpecificationAggregate specification : specificationAggregates) {
            if(specification.getUpdateTime() == null){
                specification.setSpecifications(null);
                specification.setValue(null);
                goodsSpecificationRepository.updateById(specification);
            }
        }
        for (LitemallGoodsProductAggregate productAggregate : productAggregates) {
            if(productAggregate.getUpdateTime() == null) {
                goodsProductRepository.updateGoodsById(productAggregate);
            }
        }
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
        for (LitemallGoodsProductAggregate product : productAggregates) {
            cartService.updateProduct(Integer.valueOf(product.getGoodsProductId().getId()), goodsAggregate.getGoodsSn(), goodsAggregate.getGoodsName(),
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
    public LitemallGoodsAggregate getGoodsAggregateById(LitemallGoodsId goodsId) {
       return goodsRepository.findById(goodsId);
    }

    @Override
    public List<LitemallGoodsProductAggregate> getProductsByGoodsId(LitemallGoodsId goodsId) {
        return goodsProductRepository.findByGoodsId(goodsId);
    }

    @Override
    public List<LitemallGoodsAttributeAggregate> getAttributeByGoodsId(LitemallGoodsId goodsId) {
        return  goodsAttributeRepository.queryByGoodsId(goodsId);
    }

    @Override
    public List<LitemallGoodsSpecificationAggregate> getSpecificationByGoodsId(LitemallGoodsId goodsId) {
        return goodsSpecificationRepository.findSpecificationByGoodsId(goodsId);

    }

    private Object validate(GoodsAllInOne goodsAllInOne) {
        LitemallGoodsAggregate goods = goodsAllInOne.getGoods();
        String name = goods.getGoodsName();
        if (!StringUtils.hasLength(name)) {
            return ResponseUtil.badArgument();
        }
        String goodsSn = goods.getGoodsSn();
        if (!StringUtils.hasLength(goodsSn)) {
            return ResponseUtil.badArgument();
        }
        LitemallManufacturerId manufacturerId = goods.getManufacturerId();
        if (manufacturerId != null && manufacturerId.getId() != 0) {
            if (brandRepository.findById(manufacturerId) == null) {
                return ResponseUtil.badArgumentValue();
            }
        }
        LitemallCategoryId categoryId = goods.getCategoryId();
        if (categoryId != null && categoryId.getId() != 0) {
            if (categoryRepository.findById(categoryId) == null) {
                return ResponseUtil.badArgumentValue();
            }
        }

        List<LitemallGoodsAttributeAggregate> attributes = goodsAllInOne.getAttributes();
        for (LitemallGoodsAttributeAggregate attribute : attributes) {
            String attr = attribute.getAttributeValue();
            if (!StringUtils.hasLength(attr)) {
                return ResponseUtil.badArgument();
            }
            String value = attribute.getAttributeValue();
            if (StringUtils.isEmpty(value)) {
                return ResponseUtil.badArgument();
            }
        }

        List<LitemallGoodsSpecificationAggregate> specifications = goodsAllInOne.getSpecifications();
        for (LitemallGoodsSpecificationAggregate specification : specifications) {
            String spec = specification.getSpecifications();
            if (!StringUtils.hasLength(spec)) {
                return ResponseUtil.badArgument();
            }
            String value = specification.getValue();
            if (!StringUtils.hasLength(value)) {
                return ResponseUtil.badArgument();
            }
        }

        List<LitemallGoodsProductAggregate> products = goodsAllInOne.getProducts();
        for (LitemallGoodsProductAggregate product : products) {
            Integer number = product.getNumber();
            if (number == null || number < 0) {
                return ResponseUtil.badArgument();
            }

            LitemallMoney price = product.getPrice();
            if (price == null) {
                return ResponseUtil.badArgument();
            }

            String[] productSpecifications = product.getSpecifications();
            if (productSpecifications.length == 0) {
                return ResponseUtil.badArgument();
            }
        }
        return null;
    }

}
