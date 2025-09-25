package org.linlinjava.litemall.goods.domain.model.analysis.services;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.linlinjava.litemall.goods.domain.model.analysis.datamodel.MergedProductsData;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileMergingService {

    private static final Logger logger = LoggerFactory.getLogger(FileMergingService.class);

    @Value("${data.store.directory:/data/product-cache}")
    private String dataDirectory;

    @Value("${merge.max.files:50}")
    private int maxFilesToMerge;

    @Autowired
    private MessageChannel productTrackingChannel;

    @ServiceActivator(inputChannel = "fileMergingChannel")
    public void processFileMerge(String lastFilename) {
        logger.info("Starting file merging process triggered by: {}", lastFilename);

        try{
            // 1. Read all recent files
            List<File> dataFiles = getRecentDataFiles();

            // 2. Merge product data from all files
            MergedProductsData mergedData = mergeProductData(dataFiles);

            // 3. Send merged data to product tracking
            sendToProductTracking(mergedData);

            logger.info("File merging completed. Processed {} files, found {} unique products",
                    dataFiles.size(), mergedData.getAllProducts().size()); // I changed getUniqueProducts() to getAllProducts() because getAllProducts() returns a Set of all products

        }catch (Exception e){
            logger.error("File merging failed", e);
        }
    }

    private List<File> getRecentDataFiles() {
        File directory = new File(dataDirectory);
        File[] files = directory.listFiles((dir, name) -> name.matches("^product-data-.*\\.json$"));

        if (files == null) {
            return Collections.emptyList();
        }
        // Sort by modification time (newest first) and limit
        return Arrays.stream(files)
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .limit(maxFilesToMerge)
                .collect(Collectors.toList());
    }

    private MergedProductsData mergeProductData(List<File> dataFiles) {
        MergedProductsData mergedData = new MergedProductsData();

        dataFiles.parallelStream().forEach(file -> {
            try {
                CJProductDataResponse fileData = readProductDataFromFile(file);
                if (fileData != null && fileData.getData() != null && fileData.getData().getList() != null) {
                    //mergedData.addProducts(file.getAbsolutePath(), fileData.getData().getList());
                }
            } catch (Exception e) {
                logger.warn("Failed to read data from file: {}", file.getName(), e);
            }
        });

        return mergedData;
    }

    private CJProductDataResponse readProductDataFromFile(File file) throws IOException {
        String content = Files.readString(file.toPath());
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper.readValue(content, CJProductDataResponse.class);
    }

    private void sendToProductTracking(MergedProductsData mergedData) {
        Message<MergedProductsData> message = MessageBuilder.withPayload(mergedData)
                .setHeader("mergeCompleteTime", LocalDateTime.now())
                .setHeader("totalFilesProcessed", mergedData.getSourceFiles().size())
                .setHeader("totalProducts", mergedData.getAllProducts().size())
                .build();

        productTrackingChannel.send(message);
    }
}
