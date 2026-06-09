package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * OCS {@code ImportSession} wire shape, returned by
 * {@code GET /indexer-api/v1/full/start/<index>?locale=<locale>}. Both names
 * are required when posting back to {@code /add}, {@code /done}, or
 * {@code /cancel} — the session is identified by the whole object, not a
 * single id field.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImportSession {

    private String finalIndexName;
    private String temporaryIndexName;

    public String getFinalIndexName() {
        return finalIndexName;
    }

    public void setFinalIndexName(String finalIndexName) {
        this.finalIndexName = finalIndexName;
    }

    public String getTemporaryIndexName() {
        return temporaryIndexName;
    }

    public void setTemporaryIndexName(String temporaryIndexName) {
        this.temporaryIndexName = temporaryIndexName;
    }
}
