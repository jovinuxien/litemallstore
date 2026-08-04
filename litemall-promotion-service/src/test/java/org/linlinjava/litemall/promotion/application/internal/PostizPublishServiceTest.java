package org.linlinjava.litemall.promotion.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallPostizPost;
import org.linlinjava.litemall.promotion.application.internal.PostizPublishServiceImpl.BatchPreview;
import org.linlinjava.litemall.promotion.application.internal.PostizPublishServiceImpl.BatchRequest;
import org.linlinjava.litemall.promotion.application.internal.PostizPublishServiceImpl.ChannelView;
import org.linlinjava.litemall.promotion.application.internal.PostizPublishServiceImpl.PostizRequestException;
import org.linlinjava.litemall.promotion.application.internal.PostizPublishServiceImpl.ProductResult;
import org.linlinjava.litemall.promotion.application.ports.PostizGatewayException;
import org.linlinjava.litemall.promotion.application.ports.PostizPort;
import org.linlinjava.litemall.promotion.application.ports.SocialCatalogPort;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallPostizPostRepository;
import org.linlinjava.litemall.promotion.infrastructure.configuration.PostizProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave-17 composer semantics: env gate, channel support verdicts, content/link/
 * image composition, schedule spread, dedup warnings, batch cap, and the
 * per-product fail-soft publish path — all against in-memory fakes.
 */
class PostizPublishServiceTest {

    // ---- fakes -------------------------------------------------------

    private static class FakePostizPort implements PostizPort {
        List<PostizChannel> channels = new ArrayList<>();
        List<ScheduledCall> calls = new ArrayList<>();
        PostizGatewayException failWith;

        record ScheduledCall(String dateUtcIso, List<ChannelPost> targets) {
        }

        @Override
        public List<PostizChannel> channels() {
            return channels;
        }

        @Override
        public List<ScheduledPost> schedulePost(String dateUtcIso, List<ChannelPost> targets) {
            calls.add(new ScheduledCall(dateUtcIso, targets));
            if (failWith != null) {
                throw failWith;
            }
            return targets.stream()
                    .map(t -> new ScheduledPost(t.integrationId(), "postiz-" + t.integrationId()))
                    .toList();
        }
    }

    private static class FakeCatalog implements SocialCatalogPort {
        Map<Integer, GoodsSocialSnapshot> goods = new HashMap<>();
        Map<Integer, LiveDeal> deals = new HashMap<>();

        @Override
        public Optional<GoodsSocialSnapshot> goodsSnapshot(Integer goodsId) {
            return Optional.ofNullable(goods.get(goodsId));
        }

        @Override
        public Optional<LiveDeal> liveDealFor(Integer goodsId) {
            return Optional.ofNullable(deals.get(goodsId));
        }

        @Override
        public List<LiveDeal> liveDeals() {
            return List.copyOf(deals.values());
        }

        @Override
        public Optional<CategoryLiveDeals> categoryLiveDeals(Integer categoryId) {
            return Optional.empty();
        }
    }

    private static class InMemoryRepo implements LitemallPostizPostRepository {
        final List<LitemallPostizPost> rows = new ArrayList<>();
        int nextId = 1;

        @Override
        public void insert(LitemallPostizPost row) {
            row.setId(nextId++);
            rows.add(row);
        }

        @Override
        public PostizPostPage page(int page, int limit) {
            return new PostizPostPage(rows.size(), List.copyOf(rows));
        }

        @Override
        public List<LitemallPostizPost> scheduledSince(List<Integer> goodsIds, LocalDateTime sinceUtc) {
            return rows.stream()
                    .filter(r -> "scheduled".equals(r.getStatus()))
                    .filter(r -> goodsIds.contains(r.getGoodsId()))
                    .filter(r -> r.getScheduleTime() != null && !r.getScheduleTime().isBefore(sinceUtc))
                    .toList();
        }
    }

    // ---- fixture -----------------------------------------------------

    private FakePostizPort port;
    private FakeCatalog catalog;
    private InMemoryRepo repo;
    private PostizProperties properties;
    private PostizPublishServiceImpl service;

    private static final String FB = "fb-integration-id";

    @BeforeEach
    void setUp() {
        port = new FakePostizPort();
        catalog = new FakeCatalog();
        repo = new InMemoryRepo();
        properties = new PostizProperties();
        properties.setBaseUrl("http://localhost:4007/api/public/v1");
        properties.setApiKey("test-key");
        service = new PostizPublishServiceImpl(port, properties, catalog, repo);

        port.channels.add(new PostizPort.PostizChannel(FB, "facebook", "Trovemo Page", "pic.png", false));
        catalog.goods.put(1, snapshot(1, "Blue Mug & Co", "/_cdn/cf/img1.jpg", "18.00"));
        catalog.goods.put(2, snapshot(2, "Red Lamp", "/_cdn/cf/img2.jpg", "25.50"));
    }

    private SocialCatalogPort.GoodsSocialSnapshot snapshot(int id, String name, String pic, String retail) {
        return new SocialCatalogPort.GoodsSocialSnapshot(id, name, new BigDecimal(retail),
                null, pic, List.of(), null);
    }

    private BatchRequest request(List<Integer> goodsIds) {
        return new BatchRequest(goodsIds, List.of(FB), "2030-01-01T10:00:00Z", 30, 7);
    }

    // ---- env gate ----------------------------------------------------

    @Test
    void unconfiguredEnvAnswersTypedErrnoEverywhere() {
        properties.setApiKey(null);
        assertFalse(service.status().enabled());
        assertEquals(760, assertThrows(PostizRequestException.class, () -> service.channelViews()).getErrno());
        assertEquals(760, assertThrows(PostizRequestException.class,
                () -> service.preview(request(List.of(1)))).getErrno());
        assertEquals(760, assertThrows(PostizRequestException.class,
                () -> service.publish(request(List.of(1)), "admin")).getErrno());
        assertEquals(760, assertThrows(PostizRequestException.class, () -> service.log(1, 20)).getErrno());
    }

    @Test
    void statusReportsChannelCountWhenReachable() {
        PostizPublishServiceImpl.Status status = service.status();
        assertTrue(status.enabled());
        assertEquals(1, status.channelCount());
    }

    // ---- channels ----------------------------------------------------

    @Test
    void channelVerdicts() {
        port.channels.add(new PostizPort.PostizChannel("li", "linkedin", "LI", null, false));
        port.channels.add(new PostizPort.PostizChannel("dis", "facebook", "Dead", null, true));
        List<ChannelView> views = service.channelViews();
        assertTrue(views.get(0).supported());
        assertNull(views.get(0).reason());
        assertFalse(views.get(1).supported());
        assertTrue(views.get(1).reason().contains("linkedin"));
        assertFalse(views.get(2).supported());
        assertTrue(views.get(2).reason().contains("disabled"));
    }

    // ---- preview composition ----------------------------------------

    @Test
    void previewComposesContentLinkAndSpreadWithoutSideEffects() {
        BatchPreview preview = service.preview(request(List.of(1, 2)));
        assertEquals(2, preview.batch().size());

        var first = preview.batch().get(0);
        assertEquals("2030-01-01T10:00:00Z", first.scheduleAt());
        assertEquals("2030-01-01T10:30:00Z", preview.batch().get(1).scheduleAt());

        String content = first.perChannel().get(0).content();
        assertTrue(content.contains("<strong>Blue Mug &amp; Co</strong>"), content);
        assertTrue(content.contains("$18.00"), content);
        assertTrue(content.contains("https://trovemo.com/product/1-blue-mug-co"), content);
        assertEquals(Map.of(), first.perChannel().get(0).settings());

        // Zero side effects: nothing hit Postiz, nothing landed in the ledger.
        assertTrue(port.calls.isEmpty());
        assertTrue(repo.rows.isEmpty());
    }

    @Test
    void liveDealPriceWinsOverRetail() {
        catalog.deals.put(1, new SocialCatalogPort.LiveDeal(9, 1, new BigDecimal("12.50"),
                new BigDecimal("18.00"), LocalDateTime.now(), LocalDateTime.now().plusDays(1)));
        BatchPreview preview = service.preview(request(List.of(1)));
        String content = preview.batch().get(0).perChannel().get(0).content();
        assertTrue(content.contains("Deal price $12.50"), content);
        assertTrue(content.contains("was $18.00"), content);
    }

    @Test
    void extensionlessImageSkipsProductAndFreesItsSlot() {
        catalog.goods.put(1, snapshot(1, "Bad Image", "/_cdn/cf/img-no-extension", "10.00"));
        BatchPreview preview = service.preview(request(List.of(1, 2)));

        var skipped = preview.batch().get(0);
        assertNull(skipped.scheduleAt());
        assertTrue(skipped.perChannel().isEmpty());
        assertTrue(skipped.warnings().get(0).contains("extension"));

        // The surviving product takes the first schedule slot.
        assertEquals("2030-01-01T10:00:00Z", preview.batch().get(1).scheduleAt());
    }

    @Test
    void offsetlessStartTimeReadsAsUtcAndBadStartIsTyped() {
        BatchRequest offsetless = new BatchRequest(List.of(1), List.of(FB), "2030-01-01T10:00:00", 0, null);
        assertEquals("2030-01-01T10:00:00Z", service.preview(offsetless).batch().get(0).scheduleAt());

        BatchRequest bad = new BatchRequest(List.of(1), List.of(FB), "tomorrow", 0, null);
        assertEquals(402, assertThrows(PostizRequestException.class, () -> service.preview(bad)).getErrno());
    }

    @Test
    void dedupWarningFromRecentLedgerRow() {
        LitemallPostizPost old = new LitemallPostizPost();
        old.setGoodsId(1);
        old.setStatus("scheduled");
        old.setScheduleTime(LocalDateTime.now(ZoneOffset.UTC).minusDays(3));
        repo.rows.add(old);

        BatchPreview preview = service.preview(request(List.of(1)));
        assertTrue(preview.batch().get(0).warnings().stream()
                .anyMatch(w -> w.contains("posted 3 days ago")), preview.batch().get(0).warnings().toString());
    }

    // ---- guards ------------------------------------------------------

    @Test
    void batchCapAndUnknownTargetsAreTyped() {
        List<Integer> tooMany = new ArrayList<>();
        for (int i = 0; i < 26; i++) {
            tooMany.add(i + 1);
        }
        assertEquals(761, assertThrows(PostizRequestException.class,
                () -> service.preview(request(tooMany))).getErrno());

        assertEquals(763, assertThrows(PostizRequestException.class,
                () -> service.preview(new BatchRequest(List.of(999), List.of(FB),
                        "2030-01-01T10:00:00Z", 0, null))).getErrno());

        assertEquals(763, assertThrows(PostizRequestException.class,
                () -> service.preview(new BatchRequest(List.of(1), List.of("nope"),
                        "2030-01-01T10:00:00Z", 0, null))).getErrno());
    }

    // ---- publish -----------------------------------------------------

    @Test
    void publishLedgersScheduledRowsWithUtcInstant() {
        List<ProductResult> results = service.publish(request(List.of(1, 2)), "admin7");

        assertEquals(2, results.size());
        assertTrue(results.get(0).channels().get(0).ok());
        assertEquals("postiz-" + FB, results.get(0).channels().get(0).postizPostId());
        assertEquals(2, port.calls.size()); // one Postiz call PER PRODUCT

        assertEquals(2, repo.rows.size());
        LitemallPostizPost row = repo.rows.get(0);
        assertEquals("scheduled", row.getStatus());
        assertEquals("facebook", row.getChannelIdentifier());
        assertEquals("admin7", row.getPostedBy());
        assertEquals(LocalDateTime.of(2030, 1, 1, 10, 0), row.getScheduleTime());
        assertEquals(7, row.getCategoryId());
    }

    @Test
    void postizRejectionFailsOnlyThatProductVerbatim() {
        port.failWith = new PostizGatewayException(
                "Your post should have at least one character or one image.", "facebook", 400, null);
        List<ProductResult> results = service.publish(request(List.of(1)), "admin");

        var outcome = results.get(0).channels().get(0);
        assertFalse(outcome.ok());
        assertEquals("facebook: Your post should have at least one character or one image.",
                outcome.error());
        assertEquals("failed", repo.rows.get(0).getStatus());
        assertEquals(outcome.error(), repo.rows.get(0).getError());
    }

    @Test
    void skippedProductNeverReachesPostizOrTheLedger() {
        catalog.goods.put(1, snapshot(1, "Bad", "/_cdn/no-ext", "5.00"));
        List<ProductResult> results = service.publish(request(List.of(1)), "admin");

        assertFalse(results.get(0).channels().get(0).ok());
        assertTrue(results.get(0).channels().get(0).error().startsWith("skipped:"));
        assertTrue(port.calls.isEmpty());
        assertTrue(repo.rows.isEmpty());
    }
}
