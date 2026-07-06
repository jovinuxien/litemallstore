package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.ProductDocument;
import org.linlinjava.litemall.goods.domain.service.elastic.LitemallProductIndexingService;
import org.linlinjava.litemall.goods.domain.service.elastic.ProductIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives re-indexing of the goods catalog into OCS. The streaming-batch shape lives here
 * (application layer) so the {@link ProductIndexer} port stays a pure list-in/commit-out
 * abstraction and the domain {@link LitemallProductIndexingService} only handles per-document
 * mapping.
 *
 * <p><b>Single source of truth (Phase 4):</b> OCS reads ONLY the native {@code litemall_goods}
 * family. CJ Dropshipping products are landed into those same tables (with {@code source='cj'}) by
 * {@code CjProductPromotionService}, so they flow through the SAME native indexing path as local
 * goods — there is no longer a separate {@code cj_<pid>} document set to append. {@code /srv/search}
 * still sees one unified, ranked index; it is just sourced entirely from the DB.
 */
@Service
public class SearchReindexService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchReindexService.class);
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

    /**
     * Full reindex: stream every on-sale native goods row (local + promoted CJ) and atomically swap
     * the whole OCS index via {@link ProductIndexer#replaceAll}. Because it is a full replace, any
     * soft-deleted / off-sale goods simply drop out of the index — no per-id delete needed.
     */
    public int reindexAll() {
        List<ProductDocument> documents = new ArrayList<>();
        int page = 1;
        while (true) {
            // Page by the unique PK: paging on add_time alone is unstable — bulk CJ promotes stamp
            // hundreds of rows with the same second, ties land on arbitrary pages per query, and
            // rows get read twice / skipped across page boundaries (observed live: 6246 read,
            // 6229 unique docs — 17 tied rows lost). Order is irrelevant for a full replace.
            List<LitemallGoods> batch = goodsService.querySelective(
                    null, null, null, page, PAGE_SIZE, "id", "asc");
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
        LOGGER.info("Reindex committed: {} documents from litemall_goods (single source)", documents.size());
        return documents.size();
    }

    /**
     * Incrementally (re)index a single native goods row by id: upsert its document when the row is a
     * live, on-sale product; otherwise drop it from the index. The DB-only equivalent of the local
     * write-path → Rabbit → consumer loop, reused by the CJ promote/enrich flow so a freshly promoted
     * CJ product appears in OCS without a full reindex.
     */
    public void reindexGoods(Integer goodsId) {
        if (goodsId == null) {
            return;
        }
        LitemallGoods goods = goodsService.findById(goodsId);
        boolean indexable = goods != null
                && Boolean.TRUE.equals(goods.getIsOnSale())
                && !Boolean.TRUE.equals(goods.getDeleted());
        if (indexable) {
            productIndexer.upsert(indexingService.createProductDocument(goods));
        } else {
            productIndexer.delete(String.valueOf(goodsId));
        }
    }
}
