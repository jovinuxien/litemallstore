package org.linlinjava.litemall.goods.application.goods.cj;

import org.linlinjava.litemall.db.dao.CjSourcingRequestMapper;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.domain.LitemallCjSourcingRequest;
import org.linlinjava.litemall.db.service.LitemallCjProductService;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productdetail.CJProductDetailData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing.CJSourcingCreateRequest;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing.CJSourcingCreateResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.sourcing.CJSourcingQueryItem;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin use-cases for CJ product sourcing. CJ owns the sourcing lifecycle; every created
 * request is persisted to {@code litemall_cj_sourcing_request} (V32) so the admin list
 * survives restarts and renders without a CJ round-trip — the local row is the projection,
 * refreshed on demand via {@code product/sourcing/query} through the paced {@link CJProductService}.
 */
@Service
public class CjSourcingService {

    private static final Logger logger = LoggerFactory.getLogger(CjSourcingService.class);

    /** Cap on how many rows one ?refresh=true sweep re-queries at CJ (one paced call total). */
    private static final int REFRESH_BATCH = 20;

    private final CjSourcingRequestMapper sourcingMapper;
    private final LitemallCjProductService cjProductStore;
    private final CJProductService cjProductService;

    public CjSourcingService(CjSourcingRequestMapper sourcingMapper,
                             LitemallCjProductService cjProductStore,
                             CJProductService cjProductService) {
        this.sourcingMapper = sourcingMapper;
        this.cjProductStore = cjProductStore;
        this.cjProductService = cjProductService;
    }

    /** Thrown when the request can't be built (unknown pid, missing name/image); maps to a 4xx errmsg. */
    public static class BadSourcingRequestException extends RuntimeException {
        public BadSourcingRequestException(String message) {
            super(message);
        }
    }

    /**
     * Create a sourcing request at CJ and persist the local projection. {@code cjPid} fills
     * name/image/url from our {@code litemall_cj_product} snapshot (live detail fallback);
     * without a pid, CJ requires {@code productName} + {@code productImage} from the caller.
     * The row is persisted even when CJ answers without a sourcing id, so a failed create is
     * visible in the admin list rather than silently lost.
     */
    public LitemallCjSourcingRequest create(String cjPid, String productName, String productImage,
                                            String productUrl, BigDecimal price, String remark) {
        if (StringUtils.hasText(cjPid)) {
            LitemallCjProduct snapshot = cjProductStore.findByPid(cjPid.trim());
            if (snapshot != null) {
                productName = StringUtils.hasText(productName) ? productName : snapshot.getTitle();
                productImage = StringUtils.hasText(productImage) ? productImage : snapshot.getImageUrl();
            } else {
                CJProductDetailData live = cjProductService.getProductDetail(cjPid.trim());
                if (live != null) {
                    String liveName = StringUtils.hasText(live.getProductNameEn())
                            ? live.getProductNameEn() : live.getProductName();
                    productName = StringUtils.hasText(productName) ? productName : liveName;
                    productImage = StringUtils.hasText(productImage) ? productImage : live.getProductImage();
                }
            }
        }
        if (!StringUtils.hasText(productName) || !StringUtils.hasText(productImage)) {
            throw new BadSourcingRequestException(
                    "productName and productImage are required (or pass a cjPid known to the catalog)");
        }

        CJSourcingCreateRequest request = new CJSourcingCreateRequest();
        request.setProductName(productName);
        request.setProductImage(productImage);
        request.setProductUrl(productUrl);
        request.setPrice(price);
        request.setRemark(remark);
        if (StringUtils.hasText(cjPid)) {
            request.setThirdProductId(cjPid.trim());
        }

        String cjSourcingId = null;
        String status = null;
        try {
            CJSourcingCreateResponse response = cjProductService.createSourcing(request);
            if (response != null && response.isOk() && response.getData() != null) {
                cjSourcingId = response.getData().getCjSourcingId();
                status = response.getData().getResult();
            } else {
                status = "create-failed: " + (response != null ? response.getMessage() : "no response");
            }
        } catch (RuntimeException ex) {
            logger.warn("CJ sourcing create failed: {}", ex.getMessage());
            status = "create-failed: " + ex.getMessage();
        }

        LitemallCjSourcingRequest row = new LitemallCjSourcingRequest();
        row.setCjSourcingId(cjSourcingId);
        row.setCjPid(StringUtils.hasText(cjPid) ? cjPid.trim() : null);
        row.setProductName(productName);
        row.setProductImage(productImage);
        row.setProductUrl(productUrl);
        row.setRemark(remark);
        row.setPrice(price);
        row.setSourceStatusStr(status);
        row.setAddTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        row.setDeleted(false);
        sourcingMapper.insert(row);
        return row;
    }

    /** One admin list page from the LOCAL projection (CJ-down safe), optionally CJ-refreshed first. */
    public Map<String, Object> list(int page, int limit, boolean refresh) {
        if (refresh) {
            refreshFromCj(sourcingMapper.selectRefreshable(REFRESH_BATCH));
        }
        int offset = Math.max(0, (page - 1) * limit);
        List<LitemallCjSourcingRequest> rows = sourcingMapper.selectPage(offset, limit);
        return Map.of("list", rows, "total", sourcingMapper.countAll(), "page", page, "limit", limit);
    }

    /** One request by CJ sourceId, refreshed from CJ when asked; null when unknown locally. */
    public LitemallCjSourcingRequest bySourceId(String sourceId, boolean refresh) {
        LitemallCjSourcingRequest row = sourcingMapper.selectByCjSourcingId(sourceId);
        if (row != null && refresh) {
            refreshFromCj(List.of(row));
            row = sourcingMapper.selectByCjSourcingId(sourceId);
        }
        return row;
    }

    /** Sync CJ's sourcing status onto the local rows; a CJ failure leaves the projection as-is. */
    private void refreshFromCj(List<LitemallCjSourcingRequest> rows) {
        List<String> sourceIds = rows.stream()
                .map(LitemallCjSourcingRequest::getCjSourcingId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (sourceIds.isEmpty()) {
            return;
        }
        List<CJSourcingQueryItem> items = cjProductService.querySourcing(new ArrayList<>(sourceIds));
        for (CJSourcingQueryItem item : items) {
            if (item == null || !StringUtils.hasText(item.getSourceId())) {
                continue;
            }
            LitemallCjSourcingRequest local = sourcingMapper.selectByCjSourcingId(item.getSourceId());
            if (local == null) {
                continue;
            }
            LitemallCjSourcingRequest patch = new LitemallCjSourcingRequest();
            patch.setId(local.getId());
            patch.setSourceStatus(item.getSourceStatus());
            patch.setSourceStatusStr(item.getSourceStatusStr());
            patch.setCjProductId(item.getCjProductId());
            patch.setCjVariantSku(item.getCjVariantSku());
            patch.setUpdateTime(LocalDateTime.now());
            sourcingMapper.updateCjProjection(patch);
        }
    }
}
