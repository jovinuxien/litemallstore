package org.linlinjava.litemall.goods.infrastructure.acl.client.cjdropshipclient.api.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.CJTokenService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.linlinjava.litemall.goods.infrastructure.configuration.CjListFilter;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wave 26 Phase 2 deliverable 4, at the URL seam: the filter must add EXACTLY the parameters
 * configured and nothing else, and an absent filter must leave the request byte-identical to the
 * pre-Wave-26 client. The latter is the important one — sourcing is opt-in, so every catalog target
 * that does not configure it has to keep behaving precisely as before.
 */
public class CJProductClientFilterTest {

    private static final String LIST_URL = "https://developers.cjdropshipping.com/api2.0/v1/product/list";

    private CapturingClient client;

    /** Captures the built URL instead of calling CJ. */
    private static class CapturingClient extends CJProductClient {
        private String lastUrl;

        CapturingClient(CJDropshippingConfig config, CJTokenService tokens) {
            super(config, null, new ObjectMapper(), tokens);
        }

        @Override
        public <T> T makeGetRequest(String url, Class<T> responseType, String accessToken, String errorMessage) {
            this.lastUrl = url;
            return null;    // a null body ends the caller's loop; this test only asserts the URL
        }
    }

    @BeforeEach
    public void setUp() {
        CJDropshippingConfig config = new CJDropshippingConfig();
        CJDropshippingConfig.Api api = new CJDropshippingConfig.Api();
        CJDropshippingConfig.Product product = new CJDropshippingConfig.Product();
        product.setListUrl(LIST_URL);
        api.setProduct(product);
        config.setApi(api);

        CJTokenService tokens = mock(CJTokenService.class);
        when(tokens.getValidToken()).thenReturn("test-token");

        client = new CapturingClient(config, tokens);
    }

    @Test
    public void absentFilterProducesTheLegacyUrl() {
        client.getProductList("LEAF-1", 2, 20);
        String legacy = client.lastUrl;

        client.getProductList("LEAF-1", 2, 20, CjListFilter.NONE);
        assertEquals(legacy, client.lastUrl, "CjListFilter.NONE must change nothing");

        client.getProductList("LEAF-1", 2, 20, null);
        assertEquals(legacy, client.lastUrl, "a null filter must change nothing either");

        assertTrue(legacy.contains("pageNum=2"));
        assertTrue(legacy.contains("pageSize=20"));
        assertTrue(legacy.contains("categoryId=LEAF-1"));
        assertFalse(legacy.contains("countryCode"), "no filter, no countryCode parameter");
        assertFalse(legacy.contains("minPrice"));
        assertFalse(legacy.contains("maxPrice"));
    }

    @Test
    public void countryAndCostBandAreAppendedVerbatim() {
        // ⚠ the price bounds are CJ COST, not our retail: at margin 2.5 a EUR 25-80 retail band
        // is roughly EUR 10-32 of cost.
        client.getProductList("LEAF-1", 1, 20,
                new CjListFilter("DE", new BigDecimal("10.00"), new BigDecimal("32.00")));

        assertTrue(client.lastUrl.contains("countryCode=DE"), client.lastUrl);
        assertTrue(client.lastUrl.contains("minPrice=10.00"), client.lastUrl);
        assertTrue(client.lastUrl.contains("maxPrice=32.00"), client.lastUrl);
    }

    @Test
    public void partialFilterOnlyAppendsWhatIsSet() {
        client.getProductList("LEAF-1", 1, 20, new CjListFilter("DE", null, null));

        assertTrue(client.lastUrl.contains("countryCode=DE"));
        assertFalse(client.lastUrl.contains("minPrice"), "an unset bound must not be sent");
        assertFalse(client.lastUrl.contains("maxPrice"));
    }
}
