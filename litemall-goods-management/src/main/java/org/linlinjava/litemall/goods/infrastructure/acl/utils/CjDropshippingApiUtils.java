package org.linlinjava.litemall.goods.infrastructure.acl.utils;

import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallMoney;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.cjcategory.CJCategoryDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProduct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class CjDropshippingApiUtils {

    // Track mappings to ensure consistency
    private static final Map<String, Integer> categoryIdMapping = new ConcurrentHashMap<>();
    private static final AtomicInteger idCounter = new AtomicInteger(10000); // Starting point



    /**
     * Convert CJ string category ID to consistent integer ID
     */
    private int convertCategoryId(String cjCategoryId) {
        return categoryIdMapping.computeIfAbsent(cjCategoryId, key -> {
            try {
                // Try to parse as long first
                long longId = Long.parseLong(cjCategoryId);

                // If it's a reasonable number, use it directly (mod to fit in int range)
                if (longId > 0 && longId < Integer.MAX_VALUE) {
                    return (int) longId;
                }

                // For very large numbers, use a hash-based approach
                return Math.abs(cjCategoryId.hashCode()) % Integer.MAX_VALUE;
            } catch (NumberFormatException e) {
                // For non-numeric IDs (UUIDs), use hash code
                return Math.abs(cjCategoryId.hashCode()) % Integer.MAX_VALUE;
            }
        });
    }
    /**
     * Filter products by category ID
     * @param cjProducts
     * @param categoryId
     * @return
     */
    public static List<CJProduct> getProductByCategory(List<CJProduct> cjProducts, String categoryId) {
        return cjProducts.stream()
                .filter(product -> product.getCategoryId().equals(categoryId))
                .collect(Collectors.toList());
    }



    public List<LitemallCategoryAggregate>  cjCategoryToLitemallCategory(CJCategoryDataResponse categoryDataResponse) {
        List<LitemallCategoryAggregate> result = new ArrayList<>();

        // First Level categories like: Women's clothing, Pets supplies, etc.
        for(CJCategoryDataResponse.CategoryData categoryData:  categoryDataResponse.getData()) {
            LitemallCategoryAggregate l1Category = new LitemallCategoryAggregate();
            int l1ID = idCounter.getAndDecrement();
            l1Category.setCategoryId(new LitemallCategoryId(l1ID));
            l1Category.setCategoryName(categoryData.getCategoryFirstName());
            l1Category.setLevel("L1");
            l1Category.setParentId(0); // Using O for root categories
            l1Category.setSortOrder(result.size() + 1);
            result.add(l1Category);

            // Second level categories (like: Accessories, Parent-child clothing, etc.
            for(CJCategoryDataResponse.CategorySecond secondLevel: categoryData.getCategoryFirstList()) {
                LitemallCategoryAggregate l2Category = new LitemallCategoryAggregate();
                int l2ID = idCounter.getAndDecrement();

                l2Category.setCategoryId(new LitemallCategoryId(l2ID));
                l2Category.setCategoryName(secondLevel.getCategorySecondName());
                l2Category.setLevel("L2");
                l2Category.setParentId(Integer.valueOf(l1Category.getCategoryId().getId()));
                l2Category.setSortOrder(secondLevel.getCategorySecondList().indexOf(secondLevel) + 1);
                result.add(l2Category);

                // Third level categories (actual product categories)
                for (CJCategoryDataResponse.CategoryThird thirdLevel : secondLevel.getCategorySecondList()) {
                    LitemallCategoryAggregate thirdLevelCategory = new LitemallCategoryAggregate();
                    int l3ID = convertCategoryId(thirdLevel.getCategoryId());
                    thirdLevelCategory.setCategoryId(new LitemallCategoryId(l3ID));
                    thirdLevelCategory.setCategoryName(thirdLevel.getCategoryName());
                    thirdLevelCategory.setLevel("L3");
                    thirdLevelCategory.setParentId(Integer.valueOf(l2Category.getCategoryId().getId()));
                    thirdLevelCategory.setSortOrder(secondLevel.getCategorySecondList().indexOf(thirdLevel) + 1);
                    result.add(thirdLevelCategory);
                }
            }
        }
        return result;
    }

    public LitemallGoodsAggregate convertProduct(CJProduct cjProduct){

        LitemallGoodsAggregate goods = new LitemallGoodsAggregate();

        goods.setGoodsId(new LitemallGoodsId(Integer.parseInt(cjProduct.getPid())));
        goods.setCategoryId(new LitemallCategoryId(convertCategoryId(cjProduct.getCategoryId())));
        goods.setGoodsSn(cjProduct.getProductSku());

        // Get the first product name from the array
        String[] names = new String[]{cjProduct.getProductName()};
        goods.setGoodsName(names[0]);

        goods.setGallery(new String[]{cjProduct.getProductImage()}); // Assuming single image for gallery
        goods.setKeyword(cjProduct.getProductNameEn());
        goods.setBrief(cjProduct.getProductNameEn());
        goods.setDetail(cjProduct.getRemark());

        goods.setOnSale(true); // Assuming all products are on sale
        goods.setSortOrder((short) 1); // Default sort order
        goods.setPicUrl(cjProduct.getProductImage());
        goods.setShareUrl(""); // No share URL in source data

        goods.setHot(false); // Default not hot
        goods.setNew(false); // Default not new
        goods.setUnit(cjProduct.getProductUnit());

        // Convert prices
        try {
            BigDecimal price = new BigDecimal(cjProduct.getSellPrice());
            goods.setCounterPrice(new LitemallMoney(price.multiply(new BigDecimal("2")))); // Assuming counter price is 2x sell price
            goods.setRetailPrice(new LitemallMoney(price));
        } catch (NumberFormatException e) {
            // Handle invalid price format
            goods.setCounterPrice(new LitemallMoney(BigDecimal.ZERO));
            goods.setRetailPrice(new LitemallMoney(BigDecimal.ZERO));
        }

        // Convert createTime from timestamp to LocalDateTime
        try {
            long timestamp = Long.parseLong(cjProduct.getCreateTime());
            goods.setAddTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
        } catch (NumberFormatException e) {
            goods.setAddTime(LocalDateTime.now());
        }

        goods.setUpdateTime(LocalDateTime.now());
        goods.setDeleted(false);

        return goods;
    }

    private int safeConvertToInt(String idStr) {
        try {
            // First try parsing as long, then mod to fit in int range
            long longId = Long.parseLong(idStr);
            return (int) (longId % Integer.MAX_VALUE);
        } catch (NumberFormatException e) {
            // Fallback to hash code
            return idStr.hashCode() & 0x7FFFFFFF;
        }
    }


}
