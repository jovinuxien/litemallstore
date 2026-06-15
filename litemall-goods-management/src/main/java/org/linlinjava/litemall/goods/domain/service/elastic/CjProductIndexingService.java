package org.linlinjava.litemall.goods.domain.service.elastic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.ProductDocument;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Maps persisted CJ Dropshipping snapshot rows ({@code litemall_cj_product}) into the SAME flat OCS
 * {@link ProductDocument} shape as local goods, so {@code /srv/search} blends both origins into ONE
 * ranked list. The {@code cj_} prefix touches only {@link ProductDocument#getProductId()} +
 * {@link ProductDocument#getSource()} — searchable content (title/price/category) is normalized to
 * be indistinguishable in shape from a local document.
 *
 * <p><b>Storage decision (ADR — superseded 2026-06: Redis-staged DB snapshot).</b> CJ products are
 * fetched (rate-limited) into a Redis staging buffer, then NORMALIZED and persisted into the
 * dedicated {@code litemall_cj_product} table by {@code CjSnapshotSyncService} (the single home of CJ
 * normalization). This service now does NO CJ API calls and NO normalization — it reads the persisted
 * rows and maps each to a document, exactly the way {@link LitemallProductIndexingService} maps a
 * {@code litemall_goods} row. The snapshot's primary key is the RAW CJ pid (a UUID); the {@code cj_}
 * prefix is applied HERE, only when forming the OCS document id, so the raw pid stays usable for CJ
 * detail fetch + order placement. (This replaced the earlier index-only design where documents were
 * built straight from the in-memory CJ cache.)
 */
@Service
public class CjProductIndexingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CjProductIndexingService.class);

    /** Marks CJ documents; the order service routes a checkout line to CJ on this prefix / source. */
    public static final String CJ_ID_PREFIX = "cj_";
    public static final String SOURCE_CJ = "cj_dropshipping";

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    private final LitemallCjProductService cjProductStore;
    private final CJDropshippingConfig config;
    private final ObjectMapper objectMapper;

    public CjProductIndexingService(LitemallCjProductService cjProductStore,
                                    CJDropshippingConfig config,
                                    ObjectMapper objectMapper) {
        this.cjProductStore = cjProductStore;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Build CJ documents from the persisted {@code litemall_cj_product} snapshot (DB-sourced, no CJ
     * API calls); empty when CJ indexing is disabled. The snapshot is kept fresh by
     * {@code CjSnapshotSyncService}; a full reindex simply reads whatever rows are currently live.
     */
    public List<ProductDocument> buildDocuments() {
        if (!config.isEnabled()) {
            return List.of();
        }
        List<LitemallCjProduct> rows = cjProductStore.queryAllLive();
        List<ProductDocument> docs = new java.util.ArrayList<>(rows.size());
        for (LitemallCjProduct row : rows) {
            if (row == null || row.getPid() == null || row.getPid().isBlank()) {
                continue;
            }
            try {
                docs.add(toDocument(row));
            } catch (RuntimeException ex) {
                LOGGER.warn("Skipping malformed CJ snapshot row pid={}: {}", row.getPid(), ex.getMessage());
            }
        }
        LOGGER.info("Built {} CJ product documents from the snapshot for OCS", docs.size());
        return docs;
    }

    /** Map a single persisted snapshot row to the flat OCS document (id {@code cj_<pid>}, source CJ). */
    public ProductDocument toDocument(LitemallCjProduct row) {
        ProductDocument doc = new ProductDocument();
        doc.setProductId(CJ_ID_PREFIX + row.getPid()); // cj_ prefix applied ONLY here
        doc.setSource(row.getSource() != null ? row.getSource() : SOURCE_CJ);
        doc.setTitle(row.getTitle());
        doc.setImageUrl(row.getImageUrl());
        doc.setDescription(row.getDescription());
        doc.setBrand(row.getBrand());
        doc.setPrice(row.getPrice());
        doc.setDiscountPrice(row.getDiscountPrice());
        doc.setCategoryNames(readStringList(row.getCategoryNames()));
        doc.setCategoryIds(readStringList(row.getCategoryIds()));
        for (Map<String, Object> variant : readMapList(row.getVariantsJson())) {
            doc.addVariant(variant);
        }
        for (Map.Entry<String, Object> attr : readAttributes(row.getAttributesJson()).entrySet()) {
            if (attr.getValue() != null) {
                doc.addAttribute(attr.getKey(), String.valueOf(attr.getValue()));
            }
        }
        return doc;
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, STRING_LIST);
            return list != null ? list : List.of();
        } catch (Exception ex) {
            LOGGER.warn("Unparseable CJ snapshot string-list '{}': {}", json, ex.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> readMapList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json, MAP_LIST);
            return list != null ? list : List.of();
        } catch (Exception ex) {
            LOGGER.warn("Unparseable CJ snapshot variants '{}': {}", json, ex.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> readAttributes(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            return map != null ? map : Map.of();
        } catch (Exception ex) {
            LOGGER.warn("Unparseable CJ snapshot attributes '{}': {}", json, ex.getMessage());
            return Map.of();
        }
    }
}
