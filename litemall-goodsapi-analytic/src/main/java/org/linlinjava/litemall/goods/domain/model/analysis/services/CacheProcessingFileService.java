package org.linlinjava.litemall.goods.domain.model.analysis.services;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.goods.domain.model.analysis.datamodel.MergedProductsData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CacheProcessingFileService {
    private static final Logger log = LoggerFactory.getLogger(CacheProcessingFileService.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageChannel productTrackingChannel;



    private final Set<String> processedFiles = ConcurrentHashMap.newKeySet();

    // Parse cache file to CJProductDataResponse
    public CJProductDataResponse parseCacheFile(String fileContent) {
        try {
            return objectMapper.readValue(fileContent, CJProductDataResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse cache file content", e);
            return null;
        }
    }

    // Validate response
    public boolean isValidResponse(CJProductDataResponse response) {
        return response != null && response.isResult() &&
                response.getData() != null && response.getData().getList() != null;
    }

    // Add file metadata to headers
    public Map<String, Object> addFileMetadata(CJProductDataResponse response, @Header("file_originalFile") File file) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("fileName", file.getName());
        headers.put("filePath", file.getAbsolutePath());
        headers.put("categoryId", extractCategoryId(file.getName()));
        headers.put("productCount", response.getData().getList().size());
        headers.put("processingTime", System.currentTimeMillis());
        return headers;
    }

    // Extract category ID from filename
    public String extractCategoryId(String fileName) {
        if (fileName.startsWith("category-") && fileName.endsWith(".json")) {
            return fileName.substring("category-".length(), fileName.length() - ".json".length());
        }
        return "unknown";
    }

    // Merge aggregated files
    public MergedProductsData mergeAggregatedFiles(List<Message<?>> messages) {
        MergedProductsData mergedData = new MergedProductsData();

        messages.forEach(message -> {
            CJProductDataResponse response = (CJProductDataResponse) message.getPayload();
            String fileName = (String) message.getHeaders().get("fileName");
            String categoryId = (String) message.getHeaders().get("categoryId");

            if (response.getData() != null && response.getData().getList() != null) {
                mergedData.addProducts(fileName, response.getData().getList(), response);
                processedFiles.add(fileName); // Mark as processed
            }
        });

        log.info("Merged {} files containing {} unique products",
                messages.size(), mergedData.getAllProducts().size());

        return mergedData;
    }

    // Clear processed files (for testing/reset)
    public void clearProcessedFiles() {
        processedFiles.clear();
        log.info("Cleared processed files cache");
    }

    public int getProcessedFilesCount() {
        return processedFiles.size();
    }


    public void sendToTrackingService(Message<?> message) {
        try {
            Object payload = message.getPayload();
            log.info("Sending {} merged products from {} files to tracking", payload, payload);
            // If you have a tracking channel:
            productTrackingChannel.send(message);

        } catch (Exception e) {
            log.error("Failed to send merged data to tracking", e);
        }
    }
}

