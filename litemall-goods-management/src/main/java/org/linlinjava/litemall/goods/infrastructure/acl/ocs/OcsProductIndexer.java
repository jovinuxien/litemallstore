package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.ProductDocument;
import org.linlinjava.litemall.goods.domain.service.elastic.ProductIndexer;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OCS indexer-service REST adapter. Wire contract verified against
 * {@code commerceexperts/ocs-indexer-service} running locally:
 *
 * <ul>
 *   <li>{@code GET  /indexer-api/v1/full/start/<index>?locale=<locale>}
 *       → {@link ImportSession} ({@code finalIndexName} +
 *       {@code temporaryIndexName})</li>
 *   <li>{@code POST /indexer-api/v1/full/add} body
 *       {@link AddDocumentsRequest} ({@code session} + {@code documents})
 *       → batch-add count (e.g. {@code "1"})</li>
 *   <li>{@code POST /indexer-api/v1/full/done} body {@link ImportSession}
 *       → {@code "true"} on commit</li>
 *   <li>{@code POST /indexer-api/v1/full/cancel} body {@link ImportSession}
 *       → discards the temporary index</li>
 *   <li>{@code PUT  /indexer-api/v1/update/<index>} body
 *       {@code [OcsDocument]} → {@code {id: CREATED|UPDATED|NOT_FOUND}}
 *       (PUT acts as upsert)</li>
 *   <li>{@code DELETE /indexer-api/v1/update/<index>?id=<id>}
 *       → {@code {id: DELETED|NOT_FOUND}}</li>
 * </ul>
 *
 * <p>Document shape on the wire is always
 * {@code {"id": <external-id>, "data": {<OCS field map>}}}; we keep
 * {@link ProductDocument} as the flat {@code data} POJO so the field-name
 * convention (snake_case via {@code @JsonProperty}) lives next to the schema
 * in {@code application.indexer-service.yml}.
 */
@Component
public class OcsProductIndexer implements ProductIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OcsProductIndexer.class);
    private static final int BATCH_SIZE = 200;

    private final RestTemplate restTemplate;
    private final LitemallSearchProperties properties;

    public OcsProductIndexer(LitemallSearchProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .rootUri(properties.getIndexerUrl())
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public void replaceAll(List<ProductDocument> documents) {
        ImportSession session = startSession();
        try {
            for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
                List<ProductDocument> slice = documents.subList(i, Math.min(i + BATCH_SIZE, documents.size()));
                addBatch(session, wrap(slice));
            }
            doneSession(session);
            LOGGER.info("OCS full reindex committed: index={}, temp={}, count={}",
                    session.getFinalIndexName(), session.getTemporaryIndexName(), documents.size());
        } catch (RuntimeException ex) {
            cancelSession(session);
            throw ex;
        }
    }

    @Override
    public void upsert(ProductDocument document) {
        if (document == null || document.getProductId() == null) {
            return;
        }
        restTemplate.exchange(
                "/indexer-api/v1/update/{index}",
                HttpMethod.PUT,
                jsonEntity(Collections.singletonList(new OcsDocument(document.getProductId(), document))),
                Void.class,
                properties.getIndexName());
    }

    @Override
    public void delete(String productId) {
        if (productId == null || productId.isBlank()) {
            return;
        }
        restTemplate.delete("/indexer-api/v1/update/{index}?id={id}",
                properties.getIndexName(), productId);
    }

    private ImportSession startSession() {
        ImportSession session = restTemplate.getForObject(
                "/indexer-api/v1/full/start/{index}?locale={locale}",
                ImportSession.class,
                properties.getIndexName(), properties.getLocale());
        if (session == null || session.getTemporaryIndexName() == null) {
            throw new IllegalStateException(
                    "OCS indexer returned no session for index " + properties.getIndexName());
        }
        return session;
    }

    private void addBatch(ImportSession session, List<OcsDocument> documents) {
        restTemplate.postForObject(
                "/indexer-api/v1/full/add",
                jsonEntity(new AddDocumentsRequest(session, documents)),
                String.class);
    }

    private void doneSession(ImportSession session) {
        restTemplate.postForObject(
                "/indexer-api/v1/full/done",
                jsonEntity(session),
                String.class);
    }

    private void cancelSession(ImportSession session) {
        try {
            restTemplate.postForObject(
                    "/indexer-api/v1/full/cancel",
                    jsonEntity(session),
                    String.class);
        } catch (RuntimeException ex) {
            LOGGER.warn("OCS cancel for session {} failed: {}",
                    session.getTemporaryIndexName(), ex.getMessage());
        }
    }

    private List<OcsDocument> wrap(List<ProductDocument> products) {
        List<OcsDocument> wrapped = new ArrayList<>(products.size());
        for (ProductDocument p : products) {
            if (p != null && p.getProductId() != null) {
                wrapped.add(new OcsDocument(p.getProductId(), p));
            }
        }
        return wrapped;
    }

    private HttpEntity<?> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
