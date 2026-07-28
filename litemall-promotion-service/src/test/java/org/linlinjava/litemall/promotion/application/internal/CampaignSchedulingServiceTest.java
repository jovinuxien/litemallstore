package org.linlinjava.litemall.promotion.application.internal;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.application.ports.CrawledMarketDataProvider;
import org.linlinjava.litemall.promotion.application.ports.CustomerStatisticsProvider;
import org.linlinjava.litemall.promotion.application.ports.SocialCatalogPort;
import org.linlinjava.litemall.promotion.application.ports.SocialPublishPort;
import org.linlinjava.litemall.promotion.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallPromotionCampaignAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallSocialPostAggregate;
import org.linlinjava.litemall.promotion.domain.model.commands.campaign.LitemallCampaignFromCategoryCommand;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallPromotionCampaignRepository;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallSocialPostRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCampaignId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LinkedPromotionType;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCampaignStatus;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPlatform;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPostStatus;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerStatistics;
import org.linlinjava.litemall.promotion.domain.service.LitemallCampaignTargetingDomainService;
import org.linlinjava.litemall.promotion.domain.service.LitemallPromotionOperationResult;
import org.linlinjava.litemall.promotion.infrastructure.configuration.PromotionCampaignProperties;
import org.linlinjava.litemall.promotion.infrastructure.configuration.PromotionTargetingProperties;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave-12 scheduled category campaigns: the schedule tick (activate + evaluate
 * + fire drafts / complete past-end, restart-safe because every transition is
 * DB state) and the from-category composer (live-deal target set, goodsIds
 * fallback, unpublished slug-correlated drafts, honest per-platform statuses).
 * Same in-memory-fakes style as {@link SocialDealAutoPostTest}.
 */
class CampaignSchedulingServiceTest {

    // ---- fakes -------------------------------------------------------

    private static class InMemoryCampaignRepo implements LitemallPromotionCampaignRepository {
        final List<LitemallPromotionCampaignAggregate> rows = new ArrayList<>();
        int nextId = 1;

        @Override
        public Optional<LitemallPromotionCampaignAggregate> findById(LitemallCampaignId campaignId) {
            return rows.stream()
                    .filter(c -> c.getCampaignId() != null && c.getCampaignId().getId().equals(campaignId.getId()))
                    .findFirst();
        }

        @Override
        public List<LitemallPromotionCampaignAggregate> findActive() {
            return rows.stream().filter(LitemallPromotionCampaignAggregate::isActive).collect(Collectors.toList());
        }

        @Override
        public List<LitemallPromotionCampaignAggregate> findAll() {
            return new ArrayList<>(rows);
        }

        @Override
        public void save(LitemallPromotionCampaignAggregate campaign) {
            if (campaign.getCampaignId() == null) {
                campaign.setCampaignId(new LitemallCampaignId(nextId++));
                rows.add(campaign);
            }
        }
    }

    private static class InMemoryPostRepo implements LitemallSocialPostRepository {
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
            // Real semantics: status/platform filters + 1-based pages, so the
            // draft-scan pagination in publishCampaignDrafts is exercised.
            List<LitemallSocialPostAggregate> filtered = rows.stream()
                    .filter(r -> status == null || r.getStatus() == status)
                    .filter(r -> platform == null || r.getPlatform() == platform)
                    .collect(Collectors.toList());
            int offset = Math.max(0, (page - 1) * limit);
            return filtered.stream().skip(offset).limit(limit).collect(Collectors.toList());
        }

        @Override
        public int count(LitemallSocialPostStatus status, LitemallSocialPlatform platform) {
            return page(status, platform, 1, Integer.MAX_VALUE).size();
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
            return List.of();
        }

        @Override
        public void disarm(Integer id) {
        }
    }

    private static class FakeCatalog implements SocialCatalogPort {
        final List<LiveDeal> live = new ArrayList<>();
        final Map<Integer, GoodsSocialSnapshot> snapshots = new LinkedHashMap<>();
        final Map<Integer, CategoryLiveDeals> categories = new LinkedHashMap<>();

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

        @Override
        public Optional<CategoryLiveDeals> categoryLiveDeals(Integer categoryId) {
            return Optional.ofNullable(categories.get(categoryId));
        }
    }

    private static class FakePort implements SocialPublishPort {
        final LitemallSocialPlatform platform;
        boolean enabled;
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
            return enabled ? SocialPublishResult.ok("ext-" + publishCount)
                    : SocialPublishResult.fail("adapter disabled (test)");
        }
    }

    // ---- fixtures ----------------------------------------------------

    private final InMemoryCampaignRepo campaignRepo = new InMemoryCampaignRepo();
    private final InMemoryPostRepo postRepo = new InMemoryPostRepo();
    private final FakeCatalog catalog = new FakeCatalog();
    private final FakePort fb = new FakePort(LitemallSocialPlatform.META_FB, false);
    private final FakePort ig = new FakePort(LitemallSocialPlatform.META_IG, false);
    private final FakePort tiktok = new FakePort(LitemallSocialPlatform.TIKTOK, false);
    private final PromotionCampaignProperties campaignProps = new PromotionCampaignProperties();
    private final List<LitemallDomainEvent> events = new ArrayList<>();

    private LitemallCampaignServiceImpl campaignService() {
        return new LitemallCampaignServiceImpl(
                campaignRepo,
                new LitemallCampaignTargetingDomainService((criteria, population, now) -> List.of()),
                (CustomerStatisticsProvider) since -> List.<CustomerStatistics>of(),
                (CrawledMarketDataProvider) List::of,
                new PromotionTargetingProperties(),
                events::add);
    }

    private LitemallSocialPostServiceImpl socialService() {
        return new LitemallSocialPostServiceImpl(postRepo, catalog, new SocialProperties(),
                List.of(fb, ig, tiktok));
    }

    private LitemallCampaignSchedulingServiceImpl service() {
        return new LitemallCampaignSchedulingServiceImpl(
                campaignService(), socialService(), catalog, campaignProps);
    }

    private void category(int categoryId, String name, int... dealGoodsIds) {
        List<SocialCatalogPort.LiveDeal> deals = new ArrayList<>();
        for (int goodsId : dealGoodsIds) {
            int dealId = 100 + goodsId;
            SocialCatalogPort.LiveDeal deal = new SocialCatalogPort.LiveDeal(
                    dealId, goodsId, new BigDecimal("29.00"), new BigDecimal("49.00"),
                    LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusHours(2));
            deals.add(deal);
            catalog.live.add(deal);
            catalog.snapshots.put(goodsId, new SocialCatalogPort.GoodsSocialSnapshot(
                    goodsId, "Goods " + goodsId, new BigDecimal("49.00"), null,
                    "http://img/" + goodsId, List.of(), null));
        }
        catalog.categories.put(categoryId, new SocialCatalogPort.CategoryLiveDeals(categoryId, name, deals));
    }

    private LitemallCampaignFromCategoryCommand command(int categoryId,
                                                        LocalDateTime start, LocalDateTime stop,
                                                        String... platforms) {
        return LitemallCampaignFromCategoryCommand.builder()
                .categoryL1Id(categoryId)
                .schedule(new LitemallCampaignFromCategoryCommand.Schedule(start, stop))
                .platforms(List.of(platforms))
                .build();
    }

    private LitemallPromotionCampaignAggregate campaign(int id) {
        return campaignRepo.findById(new LitemallCampaignId(id)).orElseThrow();
    }

    private List<LitemallSocialPostAggregate> postsFor(int campaignId) {
        String marker = "utm_campaign=campaign-" + campaignId;
        return postRepo.rows.stream()
                .filter(r -> r.getLinkUrl() != null && r.getLinkUrl().endsWith(marker))
                .collect(Collectors.toList());
    }

    // ---- from-category composer --------------------------------------

    @Test
    void fromCategoryCreatesScheduledCampaignAndUnpublishedDrafts() {
        category(30, "Women's Clothing", 7, 8);
        LitemallPromotionOperationResult result = service().fromCategory(
                command(30, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3),
                        "meta_fb", "meta_ig"), "admin-9");

        assertTrue(result.isSuccess(), result.getMessage());
        Integer campaignId = (Integer) result.getData().get("campaignId");
        assertNotNull(campaignId);

        LitemallPromotionCampaignAggregate campaign = campaign(campaignId);
        assertEquals(LitemallCampaignStatus.DRAFT, campaign.getStatus(), "tick activates it, not the composer");
        assertEquals("Women's Clothing campaign", campaign.getName());
        assertEquals(List.of(7, 8), campaign.getTargetGoodsIds());
        assertEquals(LinkedPromotionType.SECKILL, campaign.getLinkedPromotionType());
        assertEquals(107, campaign.getLinkedPromotionId());

        List<LitemallSocialPostAggregate> drafts = postsFor(campaignId);
        assertEquals(4, drafts.size(), "2 goods × 2 platforms");
        assertTrue(drafts.stream().allMatch(r -> r.getStatus() == LitemallSocialPostStatus.DRAFT),
                "drafts stay unpublished until activation");
        assertTrue(drafts.stream().allMatch(r -> "admin-9".equals(r.getPostedBy())));
        assertTrue(drafts.stream().allMatch(r -> r.getDealId() != null), "live-deal goods carry their deal id");
        assertEquals(0, fb.publishCount + ig.publishCount);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> platforms = (List<Map<String, Object>>) result.getData().get("platforms");
        assertEquals(2, platforms.size());
        assertTrue(platforms.stream().allMatch(p -> Boolean.FALSE.equals(p.get("enabled"))
                && "disabled".equals(p.get("status"))), "honest about disabled adapters");
    }

    @Test
    void fromCategoryFallsBackToExplicitGoodsIds() {
        category(30, "Toys"); // no live deals
        catalog.snapshots.put(5, new SocialCatalogPort.GoodsSocialSnapshot(
                5, "Goods 5", new BigDecimal("19.00"), null, "http://img/5", List.of(), null));

        LitemallCampaignFromCategoryCommand cmd = command(30,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3), "meta_fb");
        cmd.setGoodsIds(List.of(5));
        LitemallPromotionOperationResult result = service().fromCategory(cmd, "admin");

        assertTrue(result.isSuccess(), result.getMessage());
        Integer campaignId = (Integer) result.getData().get("campaignId");
        assertEquals(LinkedPromotionType.NONE, campaign(campaignId).getLinkedPromotionType());
        assertEquals(1, postsFor(campaignId).size());
    }

    @Test
    void fromCategoryFailsHonestlyOnBadInput() {
        category(30, "Toys"); // exists, but no live deals and no fallback
        assertFalse(service().fromCategory(command(30,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3), "meta_fb"), "admin")
                .isSuccess(), "no target set");
        assertFalse(service().fromCategory(command(99,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3), "meta_fb"), "admin")
                .isSuccess(), "unknown category");
        assertFalse(service().fromCategory(command(30,
                LocalDateTime.now().plusHours(3), LocalDateTime.now().plusHours(1), "meta_fb"), "admin")
                .isSuccess(), "start after stop");
        assertFalse(service().fromCategory(command(30,
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusHours(1), "meta_fb"), "admin")
                .isSuccess(), "window entirely in the past");
        assertFalse(service().fromCategory(command(30,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3), "myspace"), "admin")
                .isSuccess(), "unknown platform");
        assertTrue(campaignRepo.rows.isEmpty(), "no campaign row on any failure");
        assertTrue(postRepo.rows.isEmpty(), "no draft rows on any failure");
    }

    // ---- schedule tick -----------------------------------------------

    @Test
    void tickActivatesDueCampaignAndFiresDraftsHonestly() {
        category(30, "Women's Clothing", 7);
        LitemallCampaignSchedulingServiceImpl svc = service();
        LitemallPromotionOperationResult result = svc.fromCategory(
                command(30, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(2),
                        "meta_fb", "meta_ig"), "admin");
        Integer campaignId = (Integer) result.getData().get("campaignId");

        svc.scheduleTick();

        assertEquals(LitemallCampaignStatus.ACTIVE, campaign(campaignId).getStatus());
        List<LitemallSocialPostAggregate> posts = postsFor(campaignId);
        assertEquals(2, posts.size());
        assertTrue(posts.stream().allMatch(r -> r.getStatus() == LitemallSocialPostStatus.FAILED),
                "disabled adapters ⇒ honest failed rows, never an exception");
        assertTrue(posts.stream().allMatch(r -> r.getError() != null));
        assertFalse(events.isEmpty(), "activation/evaluation events published");
    }

    @Test
    void tickPublishesToEnabledPlatformAndIsIdempotent() {
        fb.enabled = true;
        category(30, "Women's Clothing", 7);
        LitemallCampaignSchedulingServiceImpl svc = service();
        Integer campaignId = (Integer) svc.fromCategory(
                command(30, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(2),
                        "meta_fb", "meta_ig"), "admin")
                .getData().get("campaignId");

        svc.scheduleTick();
        svc.scheduleTick();
        // "Restart": a fresh service instance over the same repository state.
        service().scheduleTick();

        List<LitemallSocialPostAggregate> posts = postsFor(campaignId);
        assertEquals(2, posts.size(), "no duplicate rows from repeat ticks/restarts");
        assertEquals(1, fb.publishCount, "one publish per draft, ever");
        assertEquals(1, posts.stream().filter(r -> r.getStatus() == LitemallSocialPostStatus.POSTED).count());
        assertEquals(1, posts.stream().filter(r -> r.getStatus() == LitemallSocialPostStatus.FAILED).count());
    }

    @Test
    void tickCompletesActivePastEndAndLeavesOthersAlone() {
        LocalDateTime now = LocalDateTime.now();
        LitemallPromotionCampaignAggregate pastEnd = LitemallPromotionCampaignAggregate.builder()
                .name("past-end").status(LitemallCampaignStatus.ACTIVE)
                .startTime(now.minusHours(2)).endTime(now.minusMinutes(1)).build();
        LitemallPromotionCampaignAggregate untimedDraft = LitemallPromotionCampaignAggregate.builder()
                .name("untimed").status(LitemallCampaignStatus.DRAFT).build();
        LitemallPromotionCampaignAggregate deadWindowDraft = LitemallPromotionCampaignAggregate.builder()
                .name("dead-window").status(LitemallCampaignStatus.DRAFT)
                .startTime(now.minusHours(2)).endTime(now.minusHours(1)).build();
        LitemallPromotionCampaignAggregate paused = LitemallPromotionCampaignAggregate.builder()
                .name("paused").status(LitemallCampaignStatus.PAUSED)
                .startTime(now.minusHours(1)).endTime(now.plusHours(1)).build();
        for (LitemallPromotionCampaignAggregate c : List.of(pastEnd, untimedDraft, deadWindowDraft, paused)) {
            campaignRepo.save(c);
        }

        service().scheduleTick();

        assertEquals(LitemallCampaignStatus.COMPLETED, pastEnd.getStatus());
        assertEquals(LitemallCampaignStatus.DRAFT, untimedDraft.getStatus(), "untimed drafts stay admin-triggered");
        assertEquals(LitemallCampaignStatus.DRAFT, deadWindowDraft.getStatus(), "slept-through windows left for the admin");
        assertEquals(LitemallCampaignStatus.PAUSED, paused.getStatus(), "pause is admin intent");
    }

    @Test
    void tickDisabledByFlagDoesNothing() {
        campaignProps.setSchedulerEnabled(false);
        category(30, "Women's Clothing", 7);
        LitemallCampaignSchedulingServiceImpl svc = service();
        Integer campaignId = (Integer) svc.fromCategory(
                command(30, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(2), "meta_fb"), "admin")
                .getData().get("campaignId");

        svc.scheduleTick();

        assertEquals(LitemallCampaignStatus.DRAFT, campaign(campaignId).getStatus());
        assertTrue(postsFor(campaignId).stream().allMatch(r -> r.getStatus() == LitemallSocialPostStatus.DRAFT));
    }

    @Test
    void draftScanPaginatesPastOnePage() {
        fb.enabled = true;
        int goodsCount = 120; // > DRAFT_SCAN_PAGE_SIZE, so the slug scan must page
        int[] goodsIds = new int[goodsCount];
        for (int i = 0; i < goodsCount; i++) {
            goodsIds[i] = 1000 + i;
        }
        category(30, "Big Category", goodsIds);
        LitemallCampaignSchedulingServiceImpl svc = service();
        Integer campaignId = (Integer) svc.fromCategory(
                command(30, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(2), "meta_fb"), "admin")
                .getData().get("campaignId");

        svc.scheduleTick();

        assertEquals(goodsCount, postsFor(campaignId).stream()
                .filter(r -> r.getStatus() == LitemallSocialPostStatus.POSTED).count());
        assertEquals(goodsCount, fb.publishCount);
    }
}
