package org.linlinjava.litemall.goods.interfaces.rest;


import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsGoodsDocumentMapper;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsIndexerClient;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsProductDocument;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallGoodsServiceApi;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("srv/admin")
public class LitemallGoodsAdminController {

    private static final int REINDEX_PAGE_SIZE = 200;

    private final LitemallGoodsServiceApi goodsServiceApi;
    private final OcsIndexerClient indexerClient;
    private final OcsGoodsDocumentMapper mapper;

    public LitemallGoodsAdminController(LitemallGoodsServiceApi goodsServiceApi,
                                        OcsIndexerClient indexerClient,
                                        OcsGoodsDocumentMapper mapper) {
        this.goodsServiceApi = goodsServiceApi;
        this.indexerClient = indexerClient;
        this.mapper = mapper;
    }

    @GetMapping("/goods/ping")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public Object getPing(Principal principal) {
        Map<String, Object> data = new HashMap<>();
        data.put("hello", "world");
        data.put("principal username", principal.getName());
        return data;
    }

    /**
     * Full reindex of every goods record into OCS. Streams in pages of
     * {@value #REINDEX_PAGE_SIZE} via {@code getGoodsBySelective(null, ...)}
     * and pushes each page through the indexer ACL. Replaces the legacy
     * single-brand-on-startup {@code ProductIndexingCommandLineRunner}.
     */
    @PostMapping("/goods/reindex")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public Map<String, Object> fullReindex() {
        int page = 1;
        int total = 0;
        while (true) {
            List<LitemallGoodsAggregate> batch = goodsServiceApi.getGoodsBySelective(
                    null, null, null, null, null, page, REINDEX_PAGE_SIZE, null);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            List<OcsProductDocument> docs = batch.stream()
                    .map(mapper::toDocument)
                    .collect(Collectors.toCollection(ArrayList::new));
            indexerClient.importBatch(docs);
            total += docs.size();
            if (batch.size() < REINDEX_PAGE_SIZE) {
                break;
            }
            page++;
        }
        return Map.of("indexed", total);
    }
}
