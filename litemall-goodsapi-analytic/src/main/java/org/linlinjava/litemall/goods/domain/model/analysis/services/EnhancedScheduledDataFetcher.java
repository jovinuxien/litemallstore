package org.linlinjava.litemall.goods.domain.model.analysis.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.file.FileHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

//@Service
//public class EnhancedScheduledDataFetcher {

    //private static final Logger logger = LoggerFactory.getLogger(EnhancedScheduledDataFetcher.class);
  /*
    @Autowired
    private CJProductService cjProductService;
    @Autowired
    private MessageChannel outgoingChanges;
    @Autowired
    private MessageChannel fileMergingChannel;
    @Autowired
    private ProductTrackingService productTrackingService;
    */


    //@Scheduled(cron = "${data.fetch.cron:0 */60 * * * *}") // Scheduled fetch every 10 minutes
    /*public void scheduledDataFetch(){
        logger.info("Starting enhanced scheduled data at {}", LocalDateTime.now());

        try{
            // 1. Fetch data from API
            CJProductDataResponse productData = cjProductService.getProductsByTopCategoryNames(null, null);
            // 2. Write to new file
            String fileName = writeDataToFile(productData);
            // 3. Trigger file merging processing
            triggerFileMerging(fileName);
            logger.info("Scheduled data fetch completed, merging triggered {}", LocalDateTime.now());
        }catch (Exception e){
            logger.error("Scheduled data fetch failed", e);
        }
    }*/

   /* private String writeDataToFile(CJProductDataResponse productData){
        try {
            String jsonData = convertToJson(productData);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("YYYYmmdd_hhmmss"));
            String filename = String.format("%s-product-data.json", timestamp);

            Message<String> message = MessageBuilder.withPayload(jsonData)
                    .setHeader(FileHeaders.FILENAME, filename)
                    .setHeader("fetchTimestamp", LocalDateTime.now())
                    .build();

            outgoingChanges.send(message);
            logger.info("Data written to file: {}", filename);
            return filename;

        } catch (Exception e) {
            logger.error("Failed to write data to file", e);
            throw new RuntimeException("File writing failed", e);
        }
    }*/

   /* private void triggerFileMerging(String latestFilename) {
        Message<String> message = MessageBuilder.withPayload(latestFilename)
                .setHeader("mergeTriggerTime", LocalDateTime.now())
                .build();

        fileMergingChannel.send(message);
        logger.info("File merging triggered for: {}", latestFilename);
    }*/

    /*private String convertToJson(CJProductDataResponse data) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper.writeValueAsString(data);
    }*/
//}
