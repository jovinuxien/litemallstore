package org.linlinjava.litemall.goods.domain.model.analysis.services;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*@Service
@Slf4j
public class ProductCacheReaderService {

    @Value("${cache.directory:/data/product-cache}")
    private String cacheDirectory;

    @Autowired
    private MessageChannel productDataChannel;

    @Autowired
    private ObjectMapper objectMapper;

    private final Set<String> processedFiles = ConcurrentHashMap.newKeySet();


    *//**
     * Clear processed files cache (for testing or reset)
     *//*
    public void clearProcessedFiles() {
        processedFiles.clear();
        log.info("Cleared processed files cache");
    }

    *//**
     * Get count of processed files
     *//*
    public int getProcessedFilesCount() {
        return processedFiles.size();
    }



    *//**
     * Scheduled method to process new cache files periodically
     *//*
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void processNewCacheFiles() {
        log.debug("Checking for new cache files in: {}", cacheDirectory);

        try {
            List<Path> allFiles = getAllCacheFiles();
            List<Path> newFiles = allFiles.stream()
                    .filter(file -> !processedFiles.contains(file.toString()))
                    .toList();

            if (!newFiles.isEmpty()) {
                log.info("Found {} new cache files to process", newFiles.size());
                newFiles.forEach(this::processCacheFile);
            }

        } catch (IOException e) {
            log.error("Failed to check for new cache files", e);
        }
    }




    *//**
     * Process a single cache file and send to channel
     *//*
    private void processCacheFile(Path filePath) {
        String fileName = filePath.getFileName().toString();

        try {
            if (processedFiles.contains(filePath.toString())) {
                log.debug("File already processed: {}", fileName);
                return;
            }

            // Extract category ID from filename
            String categoryId = extractCategoryId(fileName);
            if (categoryId == null) {
                log.warn("Invalid cache file name format: {}", fileName);
                return;
            }

            // Read and parse the cache file
            CJProductDataResponse response = readCacheFile(filePath);
            if (response == null || !response.isResult() || response.getData() == null) {
                log.warn("Invalid or empty data in cache file: {}", fileName);
                return;
            }

            // Send to product data channel
            sendToProductDataChannel(categoryId, response, filePath);

            // Mark as processed
            processedFiles.add(filePath.toString());
            log.debug("Successfully processed cache file: {}", fileName);

        } catch (Exception e) {
            log.error("Failed to process cache file: {}", fileName, e);
        }
    }


    *//**
     * Extract category ID from filename
     *//*
    private String extractCategoryId(String fileName) {
        // Expected format: category-{categoryId}.json
        if (fileName.startsWith("category-") && fileName.endsWith(".json")) {
            return fileName.substring("category-".length(), fileName.length() - ".json".length());
        }
        return null;
    }
    *//**
     * Read and parse cache file
     *//*
    private CJProductDataResponse readCacheFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            return objectMapper.readValue(content, CJProductDataResponse.class);
        } catch (Exception e) {
            log.error("Failed to read cache file: {}", filePath.getFileName(), e);
            return null;
        }
    }
    *//**
     * Send data to product data channel
     *//*
    private void sendToProductDataChannel(String categoryId, CJProductDataResponse response, Path filePath) {
        try {
            String jsonData = objectMapper.writeValueAsString(response);
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();

            Message<String> message = MessageBuilder.withPayload(jsonData)
                    .setHeader("categoryId", categoryId)
                    .setHeader("source", "cache-file")
                    .setHeader("fileName", filePath.getFileName().toString())
                    .setHeader("lastModified", lastModified)
                    .setHeader("productCount", response.getData().getList().size())
                    .setHeader("processingTime", System.currentTimeMillis())
                    .build();

            boolean sent = productDataChannel.send(message);

            if (sent) {
                log.info("Sent {} products from category {} to productDataChannel",
                        response.getData().getList().size(), categoryId);
            } else {
                log.warn("Failed to send data for category {} to productDataChannel", categoryId);
            }

        } catch (Exception e) {
            log.error("Failed to send data to channel for category: {}", categoryId, e);
        }
    }


    *//**
     * Get all cache files from directory
     *//*
    private List<Path> getAllCacheFiles() throws IOException {
        Path cacheDir = Paths.get(cacheDirectory);
        if (!Files.exists(cacheDir)) {
            log.warn("Cache directory does not exist: {}", cacheDirectory);
            return Collections.emptyList();
        }

        return Files.list(cacheDir)
                .filter(path -> path.toString().endsWith(".json"))
                .filter(path -> path.getFileName().toString().startsWith("category-"))
                .sorted(Comparator.comparingLong(this::getFileLastModified).reversed())
                .collect(Collectors.toList());
    }

    *//**
     * Get file last modified time
     *//*
    private long getFileLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}*/
