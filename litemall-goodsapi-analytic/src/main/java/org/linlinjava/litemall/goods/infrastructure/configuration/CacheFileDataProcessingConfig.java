package org.linlinjava.litemall.goods.infrastructure.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.goods.domain.model.analysis.datamodel.MergedProductsData;
import org.linlinjava.litemall.goods.domain.model.analysis.services.CacheProcessingFileService;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductDataResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.*;
import org.springframework.integration.file.transformer.FileToStringTransformer;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.metadata.ConcurrentMetadataStore;
import org.springframework.integration.metadata.MetadataStore;
import org.springframework.integration.metadata.SimpleMetadataStore;
import org.springframework.integration.transformer.MessageTransformationException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Configuration
@EnableIntegration
@Slf4j
public class CacheFileDataProcessingConfig {
    @Value("${cache.directory:/data/product-cache}")
    private String cacheDirectory;

    @Bean
    public MessageChannel cacheFileChannel() {
        return new QueueChannel(100);
    }

    @Bean
    public MessageChannel fileAggregationChannel() {
        return new QueueChannel(1000);
    }

    @Autowired
    public ObjectMapper objectMapper;


    @Bean
    public MessageChannel mergedProductChannel() {
        return new DirectChannel();
    }

    public MessageChannel productDataChannel(){
        return new QueueChannel(1000); // Large queue for bulk processing
    }


    @Bean
    public MessageChannel productTrackingChannel(){
        return new QueueChannel(100);
    }

    @Autowired
    private CacheProcessingFileService cacheProcessingFileService;


    /**
     * @desc Checks if files are being picked up for processing
     */
    @Scheduled(fixedRate = 30000)
    public void checkFileProcessing() {
        File dir = new File(cacheDirectory);
        File[] files = dir.listFiles((d, name) -> name.matches("category-.*\\.json"));
        log.info("Found {} files in directory: {}", files.length, cacheDirectory);
    }

    // Poller for cache files every 5 minutes
    @Bean
    @InboundChannelAdapter(value = "cacheFileChannel", poller = @Poller(fixedDelay = "300000"))
    public MessageSource<File> cacheFileSource() {
        FileReadingMessageSource source = new FileReadingMessageSource();
        source.setDirectory(new File(cacheDirectory));
        source.setFilter(compositeFileFilter());
        source.setUseWatchService(true);
        source.setWatchEvents(FileReadingMessageSource.WatchEventType.CREATE, FileReadingMessageSource.WatchEventType.MODIFY);
        source.setScanEachPoll(true);
        return source;
    }

    // Process individual cache files
    @Bean
    public IntegrationFlow cacheFileProcessingFlow() {
        return IntegrationFlows.from("cacheFileChannel")
                .log(LoggingHandler.Level.INFO, "file-processing",
                        message -> "Received file: " + message.getHeaders().get(FileHeaders.FILENAME))
                .transform(new FileToStringTransformer())
                .log(LoggingHandler.Level.DEBUG, "file-content",
                        message -> "File content length: " + message.getPayload().toString().length())
                .<String, CJProductDataResponse>transform(payload -> cacheProcessingFileService.parseCacheFile(payload))
                .log(LoggingHandler.Level.INFO, "parsing-result",
                        message -> "Parsing result: " + (message.getPayload() != null ? "success" : "failed"))
                //.transform(message -> cacheProcessingFileService.parseCacheFile((String) message))
                //.filter(message -> cacheProcessingFileService.isValidResponse((CJProductDataResponse) message.getPayload())
                .<CJProductDataResponse>filter(cacheProcessingFileService::isValidResponse)
                .enrichHeaders(headers -> headers
                        .headerFunction("fileName", message -> {
                            File originalFile = (File) message.getHeaders().get(FileHeaders.ORIGINAL_FILE);
                            return originalFile != null ? originalFile.getName() : "unknown";
                        })
                        .headerFunction("filePath", message -> {
                            File originalFile = (File) message.getHeaders().get(FileHeaders.ORIGINAL_FILE);
                            return originalFile != null ? originalFile.getAbsolutePath() : "unknown";
                        })
                        .headerFunction("categoryId", message -> {
                            File originalFile = (File) message.getHeaders().get(FileHeaders.ORIGINAL_FILE);
                            return originalFile != null ?
                                    cacheProcessingFileService.extractCategoryId(originalFile.getName()) : "unknown";
                        })
                )
                .log(LoggingHandler.Level.INFO, "filter-result",
                        message -> {
                            message.getPayload();
                            return "Passed filter: " + true;
                        })
                //.enrichHeaders(message -> cacheProcessingFileService.addFileMetadata((CJProductDataResponse) message.get()))
                .channel("fileAggregationChannel")
                .get();
    }

    // Aggregate files for 5 minutes, then merge and send
    @Bean
    public IntegrationFlow fileAggregationFlow() {
        return IntegrationFlows.from("fileAggregationChannel")
                .aggregate(aggregator -> aggregator
                        .correlationStrategy(message ->
                                message.getHeaders().get("categoryId", String.class))
                        .releaseStrategy(group -> group.size() >= 5) // Smaller batch size
                        .groupTimeout(60000L) // 1 minute timeout
                        .expireGroupsUponCompletion(true)
                        .expireGroupsUponTimeout(true)
                        .sendPartialResultOnExpiry(true)
                        .outputProcessor(group -> {
                            // Preserve the entire messages, not just payloads
                            return group.getMessages();
                        })
                )
                .transform(cacheProcessingFileService::mergeAggregatedFiles)
                .channel("mergedProductChannel")
                .get();
    }

    // Final processing of merged data
    @Bean
    public IntegrationFlow mergedProductFlow(ObjectMapper objectMapper) {
        return IntegrationFlows.from("mergedProductChannel")
                .log(LoggingHandler.Level.INFO, "merged-data",
                        message -> "Processing merged data: " +
                                ((MergedProductsData) message.getPayload()).getAllProducts().size() + " products")
                .transform(Message.class, this::safelyTransformToMergedProductsData)
                .handle(cacheProcessingFileService::sendToTrackingService)
                .get();
    }


    private MergedProductsData safelyTransformToMergedProductsData(Message<?> message) {
        try {
            return transformMessageToMergedProductsData(message);
        } catch (Exception e) {
            log.error("Message transformation failed for message ID: {}",
                    message.getHeaders().getId(), e);
            throw new MessageTransformationException(message, "Transformation failed", e);
        }
    }

    private MergedProductsData transformMessageToMergedProductsData(Message<?> message)
            throws IOException {

        Object payload = message.getPayload();

        if (payload instanceof MergedProductsData) {
            return (MergedProductsData) payload;
        } else if (payload instanceof String jsonString) {
            return objectMapper.readValue(jsonString, MergedProductsData.class);
        } else if (payload instanceof byte[] jsonBytes) {
            return objectMapper.readValue(jsonBytes, MergedProductsData.class);
        } else {
            throw new IllegalArgumentException(
                    String.format("Cannot convert payload type %s to MergedProductsData",
                            payload.getClass().getName()));
        }
    }


    @Bean
    public CompositeFileListFilter<File> compositeFileFilter() {
        List<FileListFilter<File>> filters = new ArrayList<>();

        // Add filename pattern filter
        filters.add(new SimplePatternFileListFilter("category-*.json"));

        // Add last modified filter (only files modified in last 24 hours)
        //filters.add(new LastModifiedFileListFilter(24 * 60 * 60 * 1000));
        // Add last modified filter (only files modified in last 5 minutes)
        filters.add(new LastModifiedFileListFilter(5 * 60 * 1000));

        filters.add(new FileSystemPersistentAcceptOnceFileListFilter((ConcurrentMetadataStore) metadataStore(), "file-processed-"));
        // Add accept once filter to avoid processing same files
        //filters.add(new AcceptOnceFileListFilter());
        return new CompositeFileListFilter<>(filters);
    }

    @Bean
    public MetadataStore metadataStore() {
        return new SimpleMetadataStore(); // Or use persistent store
    }
}
