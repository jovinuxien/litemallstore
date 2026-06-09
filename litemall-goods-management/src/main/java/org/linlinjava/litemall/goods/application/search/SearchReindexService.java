package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.ProductDocument;
import org.linlinjava.litemall.goods.domain.service.elastic.LitemallProductIndexingService;
import org.linlinjava.litemall.goods.domain.service.elastic.ProductIndexer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives full re-indexing of the goods catalog into OCS. The streaming-batch
 * shape lives here (application layer) so the {@link ProductIndexer} port
 * stays a pure list-in/commit-out abstraction and the domain
 * {@link LitemallProductIndexingService} only handles per-document mapping.
 */
@Service
public class SearchReindexService {

    private static final int PAGE_SIZE = 200;

    private final LitemallGoodsService goodsService;
    private final LitemallProductIndexingService indexingService;
    private final ProductIndexer productIndexer;

    public SearchReindexService(LitemallGoodsService goodsService,
                                LitemallProductIndexingService indexingService,
                                ProductIndexer productIndexer) {
        this.goodsService = goodsService;
        this.indexingService = indexingService;
        this.productIndexer = productIndexer;
    }

    public int reindexAll() {
        List<ProductDocument> documents = new ArrayList<>();
        int page = 1;
        while (true) {
            List<LitemallGoods> batch = goodsService.querySelective(
                    null, null, null, page, PAGE_SIZE, "add_time", "desc");
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (LitemallGoods goods : batch) {
                if (Boolean.TRUE.equals(goods.getIsOnSale())) {
                    documents.add(indexingService.createProductDocument(goods));
                }
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        productIndexer.replaceAll(documents);
        return documents.size();
    }
}
