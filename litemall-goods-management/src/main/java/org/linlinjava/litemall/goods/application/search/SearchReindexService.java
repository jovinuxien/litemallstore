package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.ProductDocument;
import org.linlinjava.litemall.goods.domain.service.elastic.CjProductIndexingService;
import org.linlinjava.litemall.goods.domain.service.elastic.LitemallProductIndexingService;
import org.linlinjava.litemall.goods.domain.service.elastic.ProductIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives full re-indexing of the goods catalog into OCS. The streaming-batch
 * shape lives here (application layer) so the {@link ProductIndexer} port
 * stays a pure list-in/commit-out abstraction and the domain
 * {@link LitemallProductIndexingService} only handles per-document mapping.
 *
 * <p>{@link ProductIndexer#replaceAll} is a full import that swaps the WHOLE index, so local
 * goods and CJ Dropshipping products must be committed together in one pass — CJ documents are
 * appended to the same list here so {@code /srv/search} sees a single unified index. A CJ
 * fetch/auth failure is logged and the reindex proceeds local-only (never fails the whole
 * rebuild); the CJ refresh scheduler keeps CJ docs current incrementally between full reindexes.
 */
@Service
public class SearchReindexService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchReindexService.class);
    private static final int PAGE_SIZE = 200;

    private final LitemallGoodsService goodsService;
    private final LitemallProductIndexingService indexingService;
    private final CjProductIndexingService cjIndexingService;
    private final ProductIndexer productIndexer;

    public SearchReindexService(LitemallGoodsService goodsService,
                                LitemallProductIndexingService indexingService,
                                CjProductIndexingService cjIndexingService,
                                ProductIndexer productIndexer) {
        this.goodsService = goodsService;
        this.indexingService = indexingService;
        this.cjIndexingService = cjIndexingService;
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
        int localCount = documents.size();

        // Append CJ Dropshipping products into the SAME full import (unified index). Graceful: a CJ
        // failure must never fail the local rebuild.
        int cjCount = 0;
        try {
            List<ProductDocument> cjDocs = cjIndexingService.buildDocuments();
            documents.addAll(cjDocs);
            cjCount = cjDocs.size();
        } catch (RuntimeException ex) {
            LOGGER.warn("CJ indexing skipped during reindex ({}); committing local-only", ex.getMessage());
        }

        productIndexer.replaceAll(documents);
        LOGGER.info("Reindex committed: {} local + {} CJ = {} documents", localCount, cjCount, documents.size());
        return documents.size();
    }
}
