package org.linlinjava.litemall.goods.infrastructure.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProduct;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProductData;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.SimplePatternFileListFilter;
import org.springframework.integration.file.transformer.FileToStringTransformer;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.json.JsonToObjectTransformer;
import org.springframework.integration.transformer.MessageTransformationException;
import org.springframework.integration.transformer.Transformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/*
@Slf4j
@Configuration
@EnableIntegration
public class ProductIntegrationConfig {

    @Value("${initial.data.file:/data/cj-products/initial-products.json}")
    private String initialDataFilePath;

    @Value("${file.polling.directory:/data/cj-products}")
    private String pollingDirectory;

    @Value("${output.file.path:/data/cj-products/merged-products.json}")
    private String outputFilePath;



    @Bean
    public MessageChannel fileChannel() {
        return new DirectChannel();
    }
    @Bean
    public MessageChannel productChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel apiChannel() {
        return new DirectChannel();
    }



    // API Source (now every 30 minutes = 1,800,000 ms), And first time and saves to file
    @Bean
    //@InboundChannelAdapter(value = "productChannel", poller = @Poller(fixedDelay = "3600000"))
    @InboundChannelAdapter(value = "productChannel", poller = @Poller(fixedDelay = "1800000"))
    public MessageSource<CJProductData> apiProductSource(CJProductService productService){
        return () -> {
            try{
                //Check if initial data file exists
                File initialFile = new File(initialDataFilePath);
                if (initialFile.exists()) {
                    log.info("Initial data file exists, Skipping initial API call...");
                    return null;
                }
                CJProductData productData = productService.fetchProductList().getData();
                log.info("Received product data: {}", productData);

                // Save data to initial data file
                try{
                    File outputFile = new File(initialDataFilePath);
                    if(!outputFile.getParentFile().exists()){
                        outputFile.getParentFile().mkdirs();
                    }
                    ObjectMapper objectMapper = new ObjectMapper();
                    objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValue(outputFile, productData);
                    log.info("Saved initial product data to file: {}", initialDataFilePath);
                } catch (Exception e) {
                    log.error("Failed to save initial product data to file", e);
                }
                return new GenericMessage<>(productData);
            } catch (Exception e) {
                log.error("Failed to fetch product list", e);
                return null;
            }
        };
    }

    // File Source
    @Bean
    @InboundChannelAdapter(value = "fileChannel", poller = @Poller(fixedDelay = "5000"))
    public MessageSource<File> fileProductSource() {
        try {
            File directory = new File(pollingDirectory);
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("Could not create directory: " + pollingDirectory);
            }

            FileReadingMessageSource source = new FileReadingMessageSource();
            source.setDirectory(directory);
            source.setFilter(new SimplePatternFileListFilter("*.json"));
            return source;
        } catch (Exception e) {
            log.error("Failed to create file source", e);
            throw new BeanCreationException("fileProductSource", "Could not create file source", e);
        }
    }

    */
/**
     * Transform file content to string: This File processing flow fetches data from file
     * makes file transformation and merge it to productChannel
     * @return
     *//*

    @Bean
    public IntegrationFlow fileProcessingFlow() {
        return IntegrationFlows.from("fileChannel")
                .transform(fileToStringTransformer())
                .transform(jsonToProductTransformer())
                .log(LoggingHandler.Level.DEBUG,
                        m -> "Received: " + m.getPayload().getClass().getSimpleName())
                .enrichHeaders(h -> h.header("processingTime", System.currentTimeMillis()))
                .channel("productChannel")
                .get();
    }

    // API Processing Flow
    @Bean
    public IntegrationFlow apiProcessingFlow() {
        return IntegrationFlows.from("apiChannel")
                .log(LoggingHandler.Level.DEBUG,
                        m -> "Processing API data with " + ((CJProductData)m.getPayload()).getList().size() + " products")
                .channel("productChannel")
                .get();
    }


    */
/**
     * The final ending flow getting data from productChannel(where data are merged from fileChannel and productChannel) for processing
     * @return
     *//*

    // Final Processing Flow with File Output
    @Bean
    public IntegrationFlow productProcessingFlow(ObjectMapper objectMapper) {
        return IntegrationFlows.from("productChannel")
                .aggregate(aggregatorSpec -> aggregatorSpec
                        .correlationStrategy(m -> true) // Aggregate all messages
                        .releaseStrategy(g -> g.size() >= 1) // Release after each message
                        .expireGroupsUponCompletion(true)
                        .groupTimeout(1000L)
                        .outputProcessor(group -> {
                            // Combine all products from different messages
                            Set<CJProduct> mergedProducts = new LinkedHashSet<>(); //Using Set to avoid duplicates

                            for(Message<?> message: group.getMessages()) {
                                Object payload = message.getPayload();
                                //System.out.println("The payload is: " + payload);
                                if(payload instanceof CJProductData) {
                                    mergedProducts.addAll(((CJProductData) payload).getList());
                                } else if(payload instanceof CJProduct) {
                                    mergedProducts.add((CJProduct) payload);
                                } else if (payload instanceof List<?>) {
                                    ((List<?>) payload).forEach(item -> {
                                        if(item instanceof CJProduct) {
                                            mergedProducts.add((CJProduct) item);
                                        } else if(item instanceof CJProductData) {
                                            mergedProducts.addAll(((CJProductData) item).getList());
                                        }
                                    });
                                }
                            }
                            // Create new merged data
                            CJProductData mergedData = new CJProductData();
                            mergedData.setList(new ArrayList<>(mergedProducts));
                            return mergedData;
                        })
                )
                .handle(message -> {
                    CJProductData productData = (CJProductData) message.getPayload();
                    List<CJProduct> mergedProducts = productData.getList();

                    if (mergedProducts == null) {
                        log.error("Merged products list is null!");
                        return;
                    }

                    if (mergedProducts.isEmpty()) {
                        log.warn("Merged products list is empty!");
                    }

                    log.info("Final merged products count: {}", mergedProducts.size());

                    // Write to output file
                    File tempFile = null;
                    try {
                        File outputFile = new File(outputFilePath);
                        if (!outputFile.getParentFile().exists()) {
                            outputFile.getParentFile().mkdirs();
                        }

                        // Temporary debug output
                        String json = objectMapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(mergedProducts);
                        log.debug("JSON to be written: {}", json);


                        objectMapper.writerWithDefaultPrettyPrinter()
                                .writeValue(outputFile, mergedProducts);
                        System.out.println("File written to file location: " + outputFile.getAbsolutePath());

                        // Read back the file to verify
                        if (outputFile.exists()) {
                            System.out.println("=== FILE CONTENT VERIFICATION ===");
                            String fileContent = new String(Files.readAllBytes(outputFile.toPath()));
                            System.out.println("File size: " + outputFile.length() + " bytes");
                            System.out.println(fileContent.isEmpty() ? "FILE IS EMPTY" : fileContent);
                        } else {
                            System.out.println("ERROR: File was not created");
                        }
                        log.info("Successfully wrote {} products to {}", mergedProducts.size(), outputFilePath);
                    } catch (Exception e) {
                        log.error("Failed to write products to file", e);
                        e.printStackTrace();
                    }
                })
                .get();
    }

    @Bean
    public Transformer fileToStringTransformer() {
        return new FileToStringTransformer();
    }

    @Bean
    public Transformer jsonToProductTransformer() {
        return new Transformer() {
            private final JsonToObjectTransformer lisTransformer = new JsonToObjectTransformer(List.class);
            private final JsonToObjectTransformer objectTransformer = new JsonToObjectTransformer(CJProductData.class);

            @Override
            public Message<?> transform(Message<?> message) {
                try{
                    return objectTransformer.transform(message);
                }catch (MessageTransformationException e) {
                    try{
                        Message<?> transformedMessage = (Message<?>) lisTransformer.transform(message);
                        // If that fails, try to parse as List<CJProduct>
                        @SuppressWarnings("unchecked")  // We know the payload is a List<CJProduct> so we suppress the warning.
                        List<CJProduct> products = (List<CJProduct>) transformedMessage.getPayload();

                        //Convert to CJProductData structure
                        CJProductData productData = new CJProductData();
                        productData.setList(products);
                        return MessageBuilder.withPayload(productData).copyHeaders(message.getHeaders()).build();
                    } catch (Exception ex) {
                        log.error("Failed to transform message payload: {}", message.getPayload());
                        throw ex;
                    }
                }
            }
        };
    }
}
*/
