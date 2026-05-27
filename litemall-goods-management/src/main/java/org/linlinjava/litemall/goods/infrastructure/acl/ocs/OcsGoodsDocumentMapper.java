package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallGoodsAggregate;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Maps {@link LitemallGoodsAggregate} → {@link OcsProductDocument} for the
 * OCS indexer. Field-by-field mapping:
 * <ul>
 *   <li>product_id ← goodsId.getId()</li>
 *   <li>title ← goodsName</li>
 *   <li>price ← retailPrice</li>
 *   <li>discount_price ← counterPrice (legacy litemall: counterPrice is
 *       the "was" price; retailPrice is the current. Adjust if your
 *       semantics differ.)</li>
 *   <li>description ← brief</li>
 *   <li>image_url ← picUrl</li>
 *   <li>brand ← manufacturerId (TODO: resolve to manufacturer name via
 *       LitemallManufactureRepository in a follow-up)</li>
 *   <li>category_names ← TODO: resolve via LitemallCatalogRepository</li>
 *   <li>category_ids ← [categoryId] for now; full breadcrumb is a follow-up</li>
 * </ul>
 */
@Component
public class OcsGoodsDocumentMapper {

    public OcsProductDocument toDocument(LitemallGoodsAggregate goods) {
        OcsProductDocument d = new OcsProductDocument();
        d.setProductId(String.valueOf(goods.getGoodsId().getId()));
        d.setTitle(goods.getGoodsName());
        d.setPrice(goods.getRetailPrice() == null ? null : goods.getRetailPrice().getAmount());
        d.setDiscountPrice(goods.getCounterPrice() == null ? null : goods.getCounterPrice().getAmount());
        d.setDescription(goods.getBrief());
        d.setImageUrl(goods.getPicUrl());
        d.setBrand(goods.getManufacturerId() == null ? null : String.valueOf(goods.getManufacturerId().getId()));
        d.setCategoryNames(Collections.emptyList());
        d.setCategoryIds(goods.getCategoryId() == null
                ? Collections.emptyList()
                : Collections.singletonList(String.valueOf(goods.getCategoryId().getId())));
        return d;
    }
}
