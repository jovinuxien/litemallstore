package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.ProductDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wire envelope OCS expects for every indexed document: an external id, a {@code data} payload that
 * maps field names to values, and an optional {@code variants} array (each a {@code {data:{…}}}
 * sub-document of variant-level fields). Our {@link ProductDocument} is the master {@code data}; its
 * {@code @JsonIgnore} variant maps are lifted here into {@code variants} so OCS indexes them as SKUs.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OcsDocument {

    private String id;
    private ProductDocument data;
    private List<Variant> variants;

    public OcsDocument() {
    }

    public OcsDocument(String id, ProductDocument data) {
        this.id = id;
        this.data = data;
        if (data != null && data.getVariants() != null && !data.getVariants().isEmpty()) {
            this.variants = new ArrayList<>(data.getVariants().size());
            for (Map<String, Object> variant : data.getVariants()) {
                this.variants.add(new Variant(variant));
            }
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ProductDocument getData() {
        return data;
    }

    public void setData(ProductDocument data) {
        this.data = data;
    }

    public List<Variant> getVariants() {
        return variants;
    }

    public void setVariants(List<Variant> variants) {
        this.variants = variants;
    }

    /** A single SKU sub-document: variant-level fields under {@code data}. */
    public static class Variant {
        private Map<String, Object> data;

        public Variant() {
        }

        public Variant(Map<String, Object> data) {
            this.data = data;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public void setData(Map<String, Object> data) {
            this.data = data;
        }
    }
}
