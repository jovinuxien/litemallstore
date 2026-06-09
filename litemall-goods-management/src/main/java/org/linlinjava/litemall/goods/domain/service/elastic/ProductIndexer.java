package org.linlinjava.litemall.goods.domain.service.elastic;

import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.ProductDocument;

import java.util.List;

/**
 * Domain port for pushing product documents into the search index. The OCS
 * REST adapter lives in {@code infrastructure/acl/ocs} so domain code does not
 * import any OCS-specific types.
 */
public interface ProductIndexer {

    /**
     * Full re-index session: replaces the current index contents with the
     * supplied documents in batches. Implementations must commit the new
     * index atomically (so the live index never appears empty mid-reindex).
     */
    void replaceAll(List<ProductDocument> documents);

    /**
     * Insert or update a single document. Used by incremental indexing on
     * goods create/update.
     */
    void upsert(ProductDocument document);

    /**
     * Remove a document from the index. Used by incremental indexing on
     * goods delete.
     */
    void delete(String productId);
}
