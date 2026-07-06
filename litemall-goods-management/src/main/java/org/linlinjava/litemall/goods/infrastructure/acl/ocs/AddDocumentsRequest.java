package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import java.util.List;

/**
 * Body of {@code POST /indexer-api/v1/full/add}: the active import session
 * plus the batch of documents to attach to it.
 */
public class AddDocumentsRequest {

    private ImportSession session;
    private List<OcsDocument> documents;

    public AddDocumentsRequest() {
    }

    public AddDocumentsRequest(ImportSession session, List<OcsDocument> documents) {
        this.session = session;
        this.documents = documents;
    }

    public ImportSession getSession() {
        return session;
    }

    public void setSession(ImportSession session) {
        this.session = session;
    }

    public List<OcsDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<OcsDocument> documents) {
        this.documents = documents;
    }
}
