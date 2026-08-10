package org.linlinjava.litemall.goods.application.seo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallGoodsProductMapper;
import org.linlinjava.litemall.db.domain.LitemallBrand;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.service.LitemallBrandService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.linlinjava.litemall.goods.infrastructure.configuration.PublicSiteProperties;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class MetaCatalogFeedServiceTest {

    private LitemallGoodsService goodsService;
    private LitemallGoodsProductMapper goodsProductMapper;
    private LitemallBrandService brandService;
    private GoogleTaxonomyMap googleTaxonomy;
    private MetaCatalogFeedService service;

    @BeforeEach
    void setup() {
        goodsService = Mockito.mock(LitemallGoodsService.class);
        goodsProductMapper = Mockito.mock(LitemallGoodsProductMapper.class);
        brandService = Mockito.mock(LitemallBrandService.class);
        googleTaxonomy = Mockito.mock(GoogleTaxonomyMap.class);
        when(googleTaxonomy.resolve(any())).thenReturn("");
        service = new MetaCatalogFeedService(goodsService, goodsProductMapper, brandService,
                new LitemallGoodsProperties(), new PublicSiteProperties(), googleTaxonomy);
        when(goodsProductMapper.selectByExample(any())).thenReturn(Collections.emptyList());
        stubGoodsPages(Collections.emptyList());
    }

    @Test
    void emitsExactHeaderAndOnSaleRowsWithFourteenFields() {
        stubGoodsPages(List.of(
                goods(1, "Wireless Earbuds", "Great sound", "https://cf.cjdropshipping.com/p/1.jpg", "19.90", "19.90", true),
                goods(2, "Off Sale Thing", "hidden", "https://cf.cjdropshipping.com/p/2.jpg", "9.90", "9.90", false)));
        stubStock(product(1, 7));

        List<List<String>> rows = parse(service.feed());

        assertThat(rows.get(0)).containsExactly(MetaCatalogFeedService.HEADER.split(",", -1));
        assertThat(rows).hasSize(2); // header + 1 on-sale row
        List<String> row = rows.get(1);
        assertThat(row).hasSize(14);
        assertThat(row.get(0)).isEqualTo("1");
        assertThat(row.get(3)).isEqualTo("in stock");
        assertThat(row.get(4)).isEqualTo("new");
        assertThat(row.get(5)).isEqualTo("19.90 USD");
        assertThat(row.get(6)).isEqualTo("https://trovemo.com/product/1-wireless-earbuds");
        assertThat(row.get(7)).isEqualTo("https://trovemo.com/_cdn/cf/p/1.jpg");
        assertThat(row.get(8)).isEmpty(); // unattributed: no fake "Trovemo" brand claim
        assertThat(row.get(10)).isEqualTo("1");
        assertThat(row.get(11)).isEmpty();
        assertThat(row.get(12)).isEqualTo("7");
        assertThat(row.get(13)).isEqualTo("false"); // identifier_exists honest for unbranded rows
    }

    @Test
    void curatedConsumerBrandLandsInBrandColumnWithBlankIdentifierExists() {
        LitemallGoods branded = goods(20, "Branded Bag", "Leather satchel",
                "https://cf.cjdropshipping.com/p/20.jpg", "49.00", "49.00", true);
        branded.setBrandId(7);
        stubGoodsPages(List.of(branded));
        when(brandService.findById(7)).thenReturn(brand(7, "Northwind", (byte) 0, true, false));

        List<String> row = parse(service.feed()).get(1);
        assertThat(row.get(8)).isEqualTo("Northwind");
        assertThat(row.get(13)).isEmpty();
    }

    @Test
    void supplierStoreAndUncuratedRowsExportBlankBrandAndFalseIdentifierExists() {
        LitemallGoods store = goods(21, "Store Good", "b",
                "https://cf.cjdropshipping.com/p/21.jpg", "10.00", "10.00", true);
        store.setBrandId(8);
        LitemallGoods uncurated = goods(22, "Uncurated Good", "b",
                "https://cf.cjdropshipping.com/p/22.jpg", "11.00", "11.00", true);
        uncurated.setBrandId(9);
        stubGoodsPages(List.of(store, uncurated));
        // kind=1 supplier store, even display-enabled, is NEVER a feed brand
        when(brandService.findById(8)).thenReturn(brand(8, "Wenling Chengdong Jiuwei", (byte) 1, true, false));
        // kind=0 but not display-enabled (uncurated) — hidden from the feed too
        when(brandService.findById(9)).thenReturn(brand(9, "Raw Brand", (byte) 0, false, false));

        List<List<String>> rows = parse(service.feed());
        assertThat(rows.get(1).get(8)).isEmpty();
        assertThat(rows.get(1).get(13)).isEqualTo("false");
        assertThat(rows.get(2).get(8)).isEmpty();
        assertThat(rows.get(2).get(13)).isEqualTo("false");
    }

    @Test
    void googleProductCategoryComesFromTheTaxonomyMap() {
        LitemallGoods g = goods(23, "Garden Hose", "b",
                "https://cf.cjdropshipping.com/p/23.jpg", "14.00", "14.00", true);
        g.setCategoryId(1036);
        stubGoodsPages(List.of(g));
        when(googleTaxonomy.resolve(1036)).thenReturn("Home & Garden");

        List<String> row = parse(service.feed()).get(1);
        assertThat(row.get(9)).isEqualTo("Home & Garden");
    }

    @Test
    void titleDuplicateBriefFallsBackToDetailProse() {
        LitemallGoods g = goods(24, "Copper Kettle", "Copper Kettle",
                "https://cf.cjdropshipping.com/p/24.jpg", "25.00", "25.00", true);
        g.setDetail("<p>Hand-hammered copper kettle with a walnut handle.</p>");
        stubGoodsPages(List.of(g));

        List<String> row = parse(service.feed()).get(1);
        assertThat(row.get(2)).startsWith("Hand-hammered copper kettle");
    }

    @Test
    void quotesCommasAndEscapesQuotesPerRfc4180() {
        stubGoodsPages(List.of(goods(3, "Knit, \"Cozy\" Cape", "Soft, warm & light",
                "https://cf.cjdropshipping.com/p/3.jpg", "29.00", "29.00", true)));

        String csv = new String(service.feed(), StandardCharsets.UTF_8);
        assertThat(csv).contains("\"Knit, \"\"Cozy\"\" Cape\"");
        assertThat(csv).contains("\"Soft, warm & light\"");

        List<List<String>> rows = parse(service.feed());
        assertThat(rows.get(1).get(1)).isEqualTo("Knit, \"Cozy\" Cape");
        assertThat(rows.get(1)).hasSize(14);
    }

    @Test
    void stripsHtmlFromDescriptionsAndNeverLeaksAngleBrackets() {
        stubGoodsPages(List.of(goods(4, "Desk Lamp",
                "<p>Bright &amp; warm<img src=\"x.jpg\"></p>\n<script>alert(1)</script> &lt;b&gt;bold&lt;/b&gt;",
                "https://cf.cjdropshipping.com/p/4.jpg", "12.00", "12.00", true)));

        String csv = new String(service.feed(), StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain("<").doesNotContain(">");
        List<List<String>> rows = parse(service.feed());
        assertThat(rows.get(1).get(2)).startsWith("Bright & warm");
        assertThat(rows.get(1).get(2)).doesNotContain("alert");
    }

    @Test
    void reCasesShoutyTitlesAndFallsBackToTitleWhenBriefBlank() {
        stubGoodsPages(List.of(goods(5, "WOMENS KNITTED CAPE", "  ",
                "https://cf.cjdropshipping.com/p/5.jpg", "57.89", "57.89", true)));

        List<List<String>> rows = parse(service.feed());
        assertThat(rows.get(1).get(1)).isEqualTo("Womens Knitted Cape");
        assertThat(rows.get(1).get(2)).isEqualTo("Womens Knitted Cape");
    }

    @Test
    void liveDealSwapEmitsAnchorPriceAndSalePrice() {
        // Lifecycle swap: retail = deal price, counter = pre-deal anchor.
        stubGoodsPages(List.of(
                goods(6, "Deal Item", "b", "https://cf.cjdropshipping.com/p/6.jpg", "40.00", "29.75", true),
                goods(7, "Plain Item", "b", "https://cf.cjdropshipping.com/p/7.jpg", "15.00", "15.00", true)));

        List<List<String>> rows = parse(service.feed());
        assertThat(rows.get(1).get(5)).isEqualTo("40.00 USD");
        assertThat(rows.get(1).get(11)).isEqualTo("29.75 USD");
        assertThat(rows.get(2).get(5)).isEqualTo("15.00 USD");
        assertThat(rows.get(2).get(11)).isEmpty();
    }

    @Test
    void skipsRowsWithoutUsableImageOrPrice() {
        stubGoodsPages(List.of(
                goods(8, "No Image", "b", null, "10.00", "10.00", true),
                goods(9, "No Price", "b", "https://cf.cjdropshipping.com/p/9.jpg", null, null, true),
                goods(10, "Aliyun Image Kept", "b",
                        "https://cc-west-usa.oss-us-west-1.aliyuncs.com/x.png", "10.00", "10.00", true)));

        List<List<String>> rows = parse(service.feed());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).get(0)).isEqualTo("10");
        assertThat(rows.get(1).get(7)).isEqualTo("https://cc-west-usa.oss-us-west-1.aliyuncs.com/x.png");
    }

    @Test
    void zeroStockIsOutOfStockWithZeroInventory() {
        stubGoodsPages(List.of(goods(11, "Empty Shelf", "b",
                "https://oss-cf.cjdropshipping.com/p/11.jpg", "10.00", "10.00", true)));

        List<List<String>> rows = parse(service.feed());
        assertThat(rows.get(1).get(3)).isEqualTo("out of stock");
        assertThat(rows.get(1).get(12)).isEqualTo("0");
        assertThat(rows.get(1).get(7)).isEqualTo("https://trovemo.com/_cdn/oss/p/11.jpg");
    }

    private void stubGoodsPages(List<LitemallGoods> all) {
        when(goodsService.querySelective(eq((Integer) null), eq((String) null), eq((String) null),
                any(Integer.class), any(Integer.class), eq("id"), eq("asc")))
                .thenAnswer(inv -> {
                    int page = inv.getArgument(3);
                    return page == 1 ? all : Collections.emptyList();
                });
    }

    private void stubStock(LitemallGoodsProduct... products) {
        when(goodsProductMapper.selectByExample(any())).thenReturn(List.of(products));
    }

    private static LitemallGoods goods(int id, String name, String brief, String picUrl,
                                       String counter, String retail, boolean onSale) {
        LitemallGoods g = new LitemallGoods();
        g.setId(id);
        g.setName(name);
        g.setBrief(brief);
        g.setPicUrl(picUrl);
        g.setCounterPrice(counter != null ? new BigDecimal(counter) : null);
        g.setRetailPrice(retail != null ? new BigDecimal(retail) : null);
        g.setIsOnSale(onSale);
        return g;
    }

    private static LitemallBrand brand(int id, String name, byte kind, boolean displayEnabled, boolean deleted) {
        LitemallBrand b = new LitemallBrand();
        b.setId(id);
        b.setName(name);
        b.setKind(kind);
        b.setDisplayEnabled(displayEnabled);
        b.setDeleted(deleted);
        return b;
    }

    private static LitemallGoodsProduct product(int goodsId, int number) {
        LitemallGoodsProduct p = new LitemallGoodsProduct();
        p.setGoodsId(goodsId);
        p.setNumber(number);
        return p;
    }

    /** Minimal RFC-4180 parser so assertions read decoded fields, not raw text. */
    private static List<List<String>> parse(byte[] csv) {
        String text = new String(csv, StandardCharsets.UTF_8);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}