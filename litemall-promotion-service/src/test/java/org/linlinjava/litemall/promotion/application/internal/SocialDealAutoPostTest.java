package org.linlinjava.litemall.promotion.application.internal;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.promotion.application.ports.SocialCatalogPort;
import org.linlinjava.litemall.promotion.application.ports.SocialPublishPort;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallSocialPostAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallSocialPostRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPlatform;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPostStatus;
import org.linlinjava.litemall.promotion.infrastructure.configuration.SocialProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The auto-poster's restart-safe dedupe: at most one auto row per
 * (deal, platform, activation), derived from DB state only — armed ledger rows
 * against currently-live deals. In-memory fakes stand in for the repository
 * (the "DB"), catalog and platform adapters; "restart" = a fresh service
 * instance over the same repository state.
 */
class SocialDealAutoPostTest {

    // ---- fakes -------------------------------------------------------

    private static class InMemoryRepo implements LitemallSocialPostRepository {
        final List<LitemallSocialPostAggregate> rows = new ArrayList<>();
        int nextId = 1;

        @Override
        public LitemallSocialPostAggregate insert(LitemallSocialPostAggregate post) {
            post.setId(nextId++);
            post.setAddTime(LocalDateTime.now());
            rows.add(post);
            return post;
        }

        @Override
        public Optional<LitemallSocialPostAggregate> findById(Integer id) {
            return rows.stream().filter(r -> r.getId().equals(id)).findFirst();
        }

        @Override
        public List<LitemallSocialPostAggregate> page(LitemallSocialPostStatus status,
                                                      LitemallSocialPlatform platform, int page, int limit) {
            return new ArrayList<>(rows);
        }

        @Override
        public int count(LitemallSocialPostStatus status, LitemallSocialPlatform platform) {
            return rows.size();
        }

        @Override
        public boolean markPosted(Integer id, String externalPostId) {
            return findById(id).map(r -> {
                r.setStatus(LitemallSocialPostStatus.POSTED);
                r.setExternalPostId(externalPostId);
                return true;
            }).orElse(false);
        }

        @Override
        public boolean markFailed(Integer id, String error) {
            return findById(id).map(r -> {
                r.setStatus(LitemallSocialPostStatus.FAILED);
                r.setError(error);
                return true;
            }).orElse(false);
        }

        @Override
        public List<LitemallSocialPostAggregate> findAutoArmed() {
            return rows.stream()
                    .filter(r -> LitemallSocialPostAggregate.POSTED_BY_AUTO.equals(r.getPostedBy()))
                    .filter(LitemallSocialPostAggregate::isAutoActive)
                    .collect(Collectors.toList());
        }

        @Override
        public void disarm(Integer id) {
            findById(id).ifPresent(r -> r.setAutoActive(false));
        }

        List<LitemallSocialPostAggregate> autoRows() {
            return rows.stream()
                    .filter(r -> LitemallSocialPostAggregate.POSTED_BY_AUTO.equals(r.getPostedBy()))
                    .collect(Collectors.toList());
        }
    }

    private static class FakeCatalog implements SocialCatalogPort {
        final List<LiveDeal> live = new ArrayList<>();
        final Map<Integer, GoodsSocialSnapshot> snapshots = new LinkedHashMap<>();

        @Override
        public Optional<GoodsSocialSnapshot> goodsSnapshot(Integer goodsId) {
            return Optional.ofNullable(snapshots.get(goodsId));
        }

        @Override
        public Optional<LiveDeal> liveDealFor(Integer goodsId) {
            return live.stream().filter(d -> d.goodsId().equals(goodsId)).findFirst();
        }

        @Override
        public List<LiveDeal> liveDeals() {
            return new ArrayList<>(live);
        }
    }

    private static class FakePort implements SocialPublishPort {
        final LitemallSocialPlatform platform;
        boolean enabled;
        boolean succeed = true;
        int publishCount = 0;

        FakePort(LitemallSocialPlatform platform, boolean enabled) {
            this.platform = platform;
            this.enabled = enabled;
        }

        @Override
        public LitemallSocialPlatform platform() {
            return platform;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public SocialPublishResult publish(SocialPublishCommand command) {
            publishCount++;
            return succeed ? SocialPublishResult.ok("ext-" + publishCount)
                    : SocialPublishResult.fail("scripted failure");
        }
    }

    // ---- fixtures ----------------------------------------------------

    private final InMemoryRepo repo = new InMemoryRepo();
    private final FakeCatalog catalog = new FakeCatalog();
    private final FakePort fb = new FakePort(LitemallSocialPlatform.META_FB, true);
    private final FakePort ig = new FakePort(LitemallSocialPlatform.META_IG, true);
    private final FakePort tiktok = new FakePort(LitemallSocialPlatform.TIKTOK, false);

    private LitemallSocialPostServiceImpl service(boolean autoPost) {
        SocialProperties props = new SocialProperties();
        props.setAutoPostDeals(autoPost);
        return new LitemallSocialPostServiceImpl(repo, catalog, props, List.of(fb, ig, tiktok));
    }

    private void activateDeal(int dealId, int goodsId, String videoUrl) {
        catalog.snapshots.put(goodsId, new SocialCatalogPort.GoodsSocialSnapshot(
                goodsId, "Goods " + goodsId, new BigDecimal("49.00"), null,
                "http://img/" + goodsId, List.of(), videoUrl));
        catalog.live.add(new SocialCatalogPort.LiveDeal(dealId, goodsId, new BigDecimal("29.00"),
                new BigDecimal("49.00"), LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1)));
    }

    // ---- tests -------------------------------------------------------

    @Test
    void disabledFlagPostsNothing() {
        activateDeal(1, 7, null);
        service(false).autoPostTick();
        assertTrue(repo.rows.isEmpty());
    }

    @Test
    void activationPostsExactlyOneRowPerEnabledPlatform() {
        activateDeal(1, 7, null);
        service(true).autoPostTick();

        List<LitemallSocialPostAggregate> auto = repo.autoRows();
        assertEquals(2, auto.size(), "meta_fb + meta_ig enabled, tiktok disabled");
        assertTrue(auto.stream().allMatch(r -> r.getDealId() == 1 && r.isAutoActive()));
        assertTrue(auto.stream().allMatch(r -> r.getStatus() == LitemallSocialPostStatus.POSTED));
        assertTrue(auto.stream().anyMatch(r -> r.getLinkUrl().contains("utm_campaign=deal-1")));
    }

    @Test
    void repeatTicksAndRestartsDoNotDuplicate() {
        activateDeal(1, 7, null);
        LitemallSocialPostServiceImpl first = service(true);
        first.autoPostTick();
        first.autoPostTick();
        // "Restart": a brand-new instance over the same repository state.
        service(true).autoPostTick();

        assertEquals(2, repo.autoRows().size(), "still one row per platform");
        assertEquals(1, fb.publishCount);
        assertEquals(1, ig.publishCount);
    }

    @Test
    void failedAutoRowStaysArmedAndIsNotRetriedBySweep() {
        fb.succeed = false;
        activateDeal(1, 7, null);
        LitemallSocialPostServiceImpl svc = service(true);
        svc.autoPostTick();
        svc.autoPostTick();

        List<LitemallSocialPostAggregate> fbRows = repo.autoRows().stream()
                .filter(r -> r.getPlatform() == LitemallSocialPlatform.META_FB)
                .collect(Collectors.toList());
        assertEquals(1, fbRows.size(), "one attempt per activation — no 1-minute retry storm");
        assertEquals(LitemallSocialPostStatus.FAILED, fbRows.get(0).getStatus());
        assertTrue(fbRows.get(0).isAutoActive(), "failed row stays armed (manual retry instead)");
        assertEquals(1, fb.publishCount);
    }

    @Test
    void deactivationDisarmsAndReactivationPostsFreshRows() {
        activateDeal(1, 7, null);
        LitemallSocialPostServiceImpl svc = service(true);
        svc.autoPostTick();
        assertEquals(2, repo.autoRows().size());

        // Deal unwound (window closed / disabled) — a tick observes it and disarms.
        catalog.live.clear();
        svc.autoPostTick();
        assertTrue(repo.autoRows().stream().noneMatch(LitemallSocialPostAggregate::isAutoActive));

        // Re-activation: fresh rows for the new activation, old ledger rows intact.
        activateDeal(1, 7, null);
        svc.autoPostTick();
        assertEquals(4, repo.autoRows().size(), "re-activation posts anew");
        assertEquals(2, repo.findAutoArmed().size());
    }

    @Test
    void tiktokEnabledWithoutVideoYieldsHonestFailedRow() {
        tiktok.enabled = true;
        tiktok.succeed = false; // the real adapter gates on videoUrl and fails
        activateDeal(1, 7, null);
        service(true).autoPostTick();

        List<LitemallSocialPostAggregate> tk = repo.autoRows().stream()
                .filter(r -> r.getPlatform() == LitemallSocialPlatform.TIKTOK)
                .collect(Collectors.toList());
        assertEquals(1, tk.size());
        assertEquals(LitemallSocialPostStatus.FAILED, tk.get(0).getStatus());
    }
}
