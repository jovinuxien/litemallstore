package org.linlinjava.litemall.goods.domain.model.analysis.services;


import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.goods.domain.model.analysis.datamodel.TrackedProducts;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProduct;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class EnhancedProductAnalysisService {


    @ServiceActivator(inputChannel = "productAnalysisChannel")
    public void analyzeTrackedProducts(TrackedProducts trackedProducts) {
        log.info("Starting analysis of {} current products, {} new products, {} disappeared products",
                trackedProducts.getCurrentProducts().size(),
                trackedProducts.getNewProducts().size(),
                trackedProducts.getDisappearedProducts().size());

        // Analyze current products
        analyzeCurrentProducts(trackedProducts.getCurrentProducts());

        // Special analysis for new products
        analyzeNewProducts(trackedProducts.getNewProducts());

        // Analysis for disappeared products
        analyzeDisappearedProducts(trackedProducts.getDisappearedProducts());

        log.info("Enhanced product analysis completed");
    }


    private void analyzeNewProducts(List<CJProduct> newProducts) {
        if (!newProducts.isEmpty()) {
            log.info("Found {} new products:", newProducts.size());
            newProducts.forEach(product ->
                    log.info(" - NEW: {} (ID: {})", product.getProductName(), product.getPid()));
        }
    }

    private void analyzeDisappearedProducts(List<CJProduct> disappearedProducts) {
        if (!disappearedProducts.isEmpty()) {
            log.info("Found {} disappeared products:", disappearedProducts.size());
            disappearedProducts.forEach(product ->
                    log.info(" - DISAPPEARED: {} (ID: {})", product.getProductName(), product.getPid()));
        }
    }

    private void analyzeCurrentProducts(List<CJProduct> currentProducts) {
        if (!currentProducts.isEmpty()) {
            log.info("Found {} current products:", currentProducts.size());
            currentProducts.forEach(product ->
                    log.info(" - CURRENT: {} (ID: {})", product.getProductName(), product.getPid()));
        }
    }

}
