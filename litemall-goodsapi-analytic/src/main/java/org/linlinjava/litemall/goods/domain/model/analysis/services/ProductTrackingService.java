package org.linlinjava.litemall.goods.domain.model.analysis.services;


import org.linlinjava.litemall.goods.domain.model.analysis.datamodel.MergedProductsData;
import org.linlinjava.litemall.goods.domain.model.analysis.datamodel.ProductTrackingInfo;
import org.linlinjava.litemall.goods.domain.model.analysis.datamodel.TrackedProducts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/*@Service
public class ProductTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(ProductTrackingService.class);

    private final Map<String, ProductTrackingInfo> productTrackingMap = new ConcurrentHashMap<>();

    private final Set<String> disappearedProducts = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Autowired
    private MessageChannel productAnalysisChannel;

    //@ServiceActivator(inputChannel = "productTrackingChannel")
    @ServiceActivator(inputChannel = "productDataChannel")
    public void trackProduct(MergedProductsData mergedData){
        logger.info("Starting product tracking for {} products from {} files",
                mergedData.getAllProducts().size(), mergedData.getSourceFiles().size());
        try {

            // 1. Track product appearances and disappearances
            TrackedProducts trackedProducts = trackProductChanges(mergedData);

            // 2. Log tracking results
            logTrackingResults(trackedProducts);

            // 3. Send to analysis
            sendToAnalysis(trackedProducts);


        } catch (Exception e) {
            logger.error("Product tracking failed", e);
        }

    }

    private TrackedProducts trackProductChanges(MergedProductsData mergedData) {
        TrackedProducts trackedProducts = new TrackedProducts();
        Set<String> currentProductIds = new HashSet<>();

        // Track current products
        mergedData.getAllProducts().forEach(product -> {
            String productId = product.getPid();
            currentProductIds.add(productId);

            ProductTrackingInfo trackingInfo = productTrackingMap.computeIfAbsent(
                    productId, id -> new ProductTrackingInfo(product)
            );

            trackingInfo.recordAppearance(mergedData.getLatestTimestamp());
            //trackedProducts.addCurrentProduct(product);
        });
        trackedProducts.addCurrentProduct(mergedData.getAllProducts());

        // Track disappeared products
        Set<String> disappeared = new HashSet<>(productTrackingMap.keySet());
        disappeared.removeAll(currentProductIds);

        disappeared.forEach(productId -> {
            ProductTrackingInfo trackingInfo = productTrackingMap.get(productId);
            if (trackingInfo != null) {
                trackingInfo.recordDisappearance();
                if (trackingInfo.isDisappeared()) {
                    disappearedProducts.add(productId);
                    trackedProducts.addDisappearedProduct(trackingInfo.getCjProduct());
                }
            }
        });

        // Clean up long-disappeared products
        cleanupOldDisappearedProducts();

        return trackedProducts;
    }

    private void cleanupOldDisappearedProducts() {
        Iterator<String> iterator = disappearedProducts.iterator();
        while (iterator.hasNext()) {
            String productId = iterator.next();
            ProductTrackingInfo trackingInfo = productTrackingMap.get(productId);
            if (trackingInfo != null && trackingInfo.shouldRemoveFromTracking()) {
                productTrackingMap.remove(productId);
                iterator.remove();
                logger.debug("Removed long-disappeared product: {}", productId);
            }
        }
    }

    private void logTrackingResults(TrackedProducts trackedProducts) {
        logger.info("Product tracking results:");
        logger.info(" - Current products: {}", trackedProducts.getCurrentProducts().size());
        logger.info(" - New products: {}", trackedProducts.getNewProducts().size());
        logger.info(" - Disappeared products: {}", trackedProducts.getDisappearedProducts().size());
        logger.info(" - Total tracked products: {}", productTrackingMap.size());
    }

    private void sendToAnalysis(TrackedProducts trackedProducts) {
        Message<TrackedProducts> message = MessageBuilder.withPayload(trackedProducts)
                .setHeader("trackingCompleteTime", LocalDateTime.now())
                .setHeader("currentProducts", trackedProducts.getCurrentProducts().size())
                .setHeader("newProducts", trackedProducts.getNewProducts().size())
                .setHeader("disappearedProducts", trackedProducts.getDisappearedProducts().size())
                .build();

        productAnalysisChannel.send(message);
    }

    public Map<String, ProductTrackingInfo> getProductTrackingMap() {
        return Collections.unmodifiableMap(productTrackingMap);
    }

}*/
