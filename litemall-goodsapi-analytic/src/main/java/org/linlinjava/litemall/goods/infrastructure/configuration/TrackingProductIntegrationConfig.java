package org.linlinjava.litemall.goods.infrastructure.configuration;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.linlinjava.litemall.goods.domain.model.analysis.datamodel.MergedProductsData;
//import org.linlinjava.litemall.goods.domain.model.analysis.services.ProductTrackingService;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
//import org.springframework.integration.transformer.GenericTransformer;
import org.springframework.integration.transformer.MessageTransformationException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Collections;

/*@Configuration
@EnableIntegration
@EnableAsync
public class TrackingProductIntegrationConfig {

    public static final Logger logger = LoggerFactory.getLogger(TrackingProductIntegrationConfig.class);

    @Bean
    public MessageChannel productTrackingChannel(){
        return new QueueChannel(100);
    }


    @Bean
    public MessageChannel productDataChannel() {
        return new QueueChannel(1000); // Large queue for bulk processing
    }


    @Bean
    public  MessageChannel productAnalysisChannel(){
        return new QueueChannel(100);
    }

    @Autowired
    public ProductTrackingService productTrackingService;


    @Bean
    public TaskExecutor trackingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setThreadNamePrefix("product-tracker-");
        executor.initialize();
        return executor;
    }

    @Bean
    public TaskExecutor cacheProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cache-processor-");
        executor.initialize();
        return executor;
    }



    @Bean
    public IntegrationFlow trackingFlow() {
        return IntegrationFlows.from("productDataChannel")
                .channel(MessageChannels.executor("trackingProcessingChannel", trackingTaskExecutor()))
                //.transform(jsonToProductDataResponseTransformer())
                //.transform(transformToMergedProductsData())
                .transform(new GenericTransformer<Message<String>, Message<CJProductDataResponse>>() {
                    @Override
                    public Message<CJProductDataResponse> transform(Message<String> message) {
                        try {
                            String payload = message.getPayload();
                            ObjectMapper objectMapper = new ObjectMapper();
                            objectMapper.registerModule(new JavaTimeModule());
                            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                            CJProductDataResponse response = objectMapper.readValue(payload, CJProductDataResponse.class);
                            return MessageBuilder.withPayload(response).copyHeaders(message.getHeaders()).build();
                        } catch (Exception e) {
                            throw new MessageTransformationException("JSON transformation failed", e);
                        }
                    }
                })
                .transform(new GenericTransformer<Message<CJProductDataResponse>, Message<MergedProductsData>>() {
                    @Override
                    public Message<MergedProductsData> transform(Message<CJProductDataResponse> message) {
                        try {
                            CJProductDataResponse response = message.getPayload();
                            String fileName = (String) message.getHeaders().get("fileName");

                            MergedProductsData mergedData = new MergedProductsData();
                            if (response.isResult() && response.getData() != null) {
                                mergedData.addProducts(fileName, response.getData().getList(), response);
                            } else {
                                mergedData.addProducts(fileName, Collections.emptyList(), response);
                            }
                            return MessageBuilder.withPayload(mergedData).copyHeaders(message.getHeaders()).build();
                        } catch (Exception e) {
                            throw new MessageTransformationException("Merged data transformation failed", e);
                        }
                    }
                })
                .handle("productTrackingService", "trackProduct")
               *//* .<MergedProductsData>handle((payload, headers) -> {
                    productTrackingService.trackProduct(payload);
                    return null;
                })*//*
                .get();
    }

}*/
