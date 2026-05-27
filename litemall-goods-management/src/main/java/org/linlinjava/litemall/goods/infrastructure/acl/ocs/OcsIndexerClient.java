package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * ACL adapter to the OCS indexer REST service (port 8535 by default).
 * Domain code calls this; the OCS HTTP contract stays behind this boundary.
 *
 * <p><b>API contract — runtime verification needed.</b> The exact endpoints
 * exposed by {@code commerceexperts/ocs-indexer-service} are not pinned in
 * this repo and the OpenAPI doc is only available against the running
 * container. The methods below assume a conventional "import session" style
 * (start → add documents → done) for batch and a single-document upsert for
 * incremental updates; adjust paths if the running container exposes a
 * different shape. Each method tags the assumption with a TODO.
 */
@Component
public class OcsIndexerClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OcsIndexerClient.class);

    private final RestTemplate restTemplate;
    private final LitemallSearchProperties properties;

    public OcsIndexerClient(RestTemplate ocsRestTemplate, LitemallSearchProperties properties) {
        this.restTemplate = ocsRestTemplate;
        this.properties = properties;
    }

    /**
     * Upsert a single product document. Used for incremental indexing on
     * goods create/update.
     *
     * TODO: verify against OCS OpenAPI doc — assumed PUT
     * {indexer-url}/index/{index-name}/{product-id} with the doc as JSON body.
     */
    public void upsert(OcsProductDocument doc) {
        String url = properties.getIndexerUrl() + "/index/" + properties.getIndexName() + "/" + doc.getProductId();
        try {
            HttpEntity<OcsProductDocument> req = new HttpEntity<>(doc, jsonHeaders());
            restTemplate.put(url, req);
        } catch (RestClientException e) {
            LOGGER.warn("OCS indexer upsert failed for product {}: {}", doc.getProductId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Delete a single product document. Used on goods delete.
     *
     * TODO: verify against OCS OpenAPI doc — assumed DELETE
     * {indexer-url}/index/{index-name}/{product-id}.
     */
    public void delete(String productId) {
        String url = properties.getIndexerUrl() + "/index/" + properties.getIndexName() + "/" + productId;
        try {
            restTemplate.delete(url);
        } catch (RestClientException e) {
            LOGGER.warn("OCS indexer delete failed for product {}: {}", productId, e.getMessage());
            throw e;
        }
    }

    /**
     * Replace the entire index contents with the given batch. Used by the
     * full-reindex admin endpoint.
     *
     * TODO: verify against OCS OpenAPI doc — assumed POST
     * {indexer-url}/import/{index-name} with JSON array body. If the real
     * contract is start-session → add-batch → done, refactor this method
     * into three calls.
     */
    public void importBatch(List<OcsProductDocument> batch) {
        String url = properties.getIndexerUrl() + "/import/" + properties.getIndexName();
        try {
            HttpEntity<List<OcsProductDocument>> req = new HttpEntity<>(batch, jsonHeaders());
            ResponseEntity<Void> resp = restTemplate.postForEntity(url, req, Void.class);
            LOGGER.info("OCS importBatch: posted {} docs to {} -> {}", batch.size(), url, resp.getStatusCode());
        } catch (RestClientException e) {
            LOGGER.warn("OCS importBatch failed ({} docs): {}", batch.size(), e.getMessage());
            throw e;
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
