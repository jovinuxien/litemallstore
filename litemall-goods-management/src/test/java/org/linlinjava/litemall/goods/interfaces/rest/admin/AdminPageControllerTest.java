package org.linlinjava.litemall.goods.interfaces.rest.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallPage;
import org.linlinjava.litemall.goods.application.content.PageService;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave-20 admin surface rules at the edge: the category whitelist on
 * create/update (junk → errno 640 naming the allowed set), the list
 * category/template filter validation, and the clone endpoint envelope.
 * {@code is_template} has no writable path anywhere in the controller.
 */
public class AdminPageControllerTest {

    private static final String EMPTY_CONFIG = "{\"version\":1,\"components\":[]}";

    private PageService service;
    private AdminPageController controller;

    @BeforeEach
    public void setUp() {
        service = Mockito.mock(PageService.class);
        // Category-validation paths short-circuit BEFORE config validation; the
        // happy paths below need a real-looking validator result.
        when(service.validateConfig(anyString()))
                .thenReturn(new org.linlinjava.litemall.goods.domain.content.PageConfigValidator(
                        new ObjectMapper()).validate(EMPTY_CONFIG));
        controller = new AdminPageController(service);
    }

    private static AdminPageController.PageUpsertRequest body(Integer id, String name,
                                                              String category, String config) {
        AdminPageController.PageUpsertRequest body = new AdminPageController.PageUpsertRequest();
        body.setId(id);
        body.setName(name);
        body.setCategory(category);
        if (config != null) {
            try {
                body.setConfig(new ObjectMapper().readTree(config));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> envelope(Object response) {
        return (Map<String, Object>) response;
    }

    // ---------------- category validation ----------------

    @Test
    public void createWithJunkCategoryIs640NamingTheAllowedSet() {
        Object response = controller.create(body(null, "Sale", "flash", EMPTY_CONFIG));
        assertThat(envelope(response).get("errno")).isEqualTo(640);
        assertThat((String) envelope(response).get("errmsg")).contains("category must be one of");
        verify(service, never()).create(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void createDefaultsToGeneralCategory() {
        when(service.create(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new LitemallPage());
        Object response = controller.create(body(null, "Sale", null, EMPTY_CONFIG));
        assertThat(envelope(response).get("errno")).isEqualTo(0);
        verify(service).create(eq("Sale"), eq(LitemallPage.POSITION_CUSTOM),
                eq(LitemallPage.CATEGORY_GENERAL), anyString());
    }

    @Test
    public void createAcceptsACouponCategory() {
        when(service.create(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new LitemallPage());
        Object response = controller.create(body(null, "Coupon page", "coupon", EMPTY_CONFIG));
        assertThat(envelope(response).get("errno")).isEqualTo(0);
        verify(service).create(eq("Coupon page"), anyString(),
                eq(LitemallPage.CATEGORY_COUPON), anyString());
    }

    /**
     * Wave 27: 'season' joined the whitelist. Without this the season page could be created
     * only by hand in SQL — the admin procedure would have no first step.
     */
    @Test
    public void createAcceptsASeasonCategory() {
        when(service.create(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new LitemallPage());
        Object response = controller.create(body(null, "Autumn spotlight", "season", EMPTY_CONFIG));
        assertThat(envelope(response).get("errno")).isEqualTo(0);
        verify(service).create(eq("Autumn spotlight"), anyString(),
                eq(LitemallPage.CATEGORY_SEASON), anyString());
    }

    @Test
    public void listForwardsTheSeasonCategoryFilter() {
        when(service.adminList(any(), any(), any(), any(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(Map.of());

        Object response = controller.list(null, "active", "season", null, 1, 10);

        assertThat(envelope(response).get("errno")).isEqualTo(0);
        verify(service).adminList(null, "active", "season", null, 1, 10);
    }

    @Test
    public void updateWithJunkCategoryIs640() {
        when(service.findById(5)).thenReturn(new LitemallPage());
        Object response = controller.update(body(5, null, "seasonal", null));
        assertThat(envelope(response).get("errno")).isEqualTo(640);
        verify(service, never()).update(any(), any(), any(), any());
    }

    @Test
    public void categoryOnlyUpdateIsAllowed() {
        when(service.findById(5)).thenReturn(new LitemallPage());
        when(service.update(eq(5), any(), eq("groupon"), any())).thenReturn(1);
        Object response = controller.update(body(5, null, "groupon", null));
        assertThat(envelope(response).get("errno")).isEqualTo(0);
        verify(service).update(5, null, "groupon", null);
    }

    // ---------------- list filters ----------------

    @Test
    public void listRejectsJunkCategoryAndTemplateFilters() {
        assertThat(envelope(controller.list(null, null, "flash", null, 1, 10)).get("errno"))
                .isEqualTo(402);
        assertThat(envelope(controller.list(null, null, null, 2, 1, 10)).get("errno"))
                .isEqualTo(402);
    }

    @Test
    public void listForwardsCategoryAndTemplateFilters() {
        when(service.adminList(any(), any(), any(), any(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(Map.of());
        Object response = controller.list(null, "draft", "coupon", 1, 1, 10);
        assertThat(envelope(response).get("errno")).isEqualTo(0);
        verify(service).adminList(null, "draft", "coupon", Boolean.TRUE, 1, 10);
    }

    // ---------------- clone ----------------

    @Test
    public void cloneReturnsTheAdminReadOfTheCopy() {
        LitemallPage copy = new LitemallPage();
        copy.setId(42);
        when(service.clone(7)).thenReturn(copy);
        when(service.adminRead(copy)).thenReturn(Map.of("id", 42));

        Object response = controller.clone(7);

        assertThat(envelope(response).get("errno")).isEqualTo(0);
        assertThat(envelope(response).get("data")).isEqualTo(Map.of("id", 42));
    }

    @Test
    public void cloneOfAMissingPageIsBadArgumentValue() {
        when(service.clone(404)).thenReturn(null);
        assertThat(envelope(controller.clone(404)).get("errno")).isEqualTo(402);
    }
}
