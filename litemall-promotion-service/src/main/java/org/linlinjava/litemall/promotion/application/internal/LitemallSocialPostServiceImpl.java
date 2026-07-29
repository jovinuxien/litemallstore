package org.linlinjava.litemall.promotion.application.internal;

import org.linlinjava.litemall.promotion.application.ports.SocialCatalogPort;
import org.linlinjava.litemall.promotion.application.ports.SocialCatalogPort.GoodsSocialSnapshot;
import org.linlinjava.litemall.promotion.application.ports.SocialCatalogPort.LiveDeal;
import org.linlinjava.litemall.promotion.application.ports.SocialPublishPort;
import org.linlinjava.litemall.promotion.application.ports.SocialPublishPort.SocialPublishCommand;
import org.linlinjava.litemall.promotion.application.ports.SocialPublishPort.SocialPublishResult;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallSocialPostAggregate;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallSocialPostRepository;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPlatform;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPostStatus;
import org.linlinjava.litemall.promotion.infrastructure.configuration.SocialProperties;
import org.linlinjava.litemall.promotion.utils.UtmShareLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Wave-6 social posting vertical: the admin composer (preview / post / list /
 * retry) and the opt-in flash-deal auto-poster. Every publish attempt — manual
 * or auto, success or failure — is one {@code litemall_social_post} ledger row;
 * platform adapters are fail-soft, so this service never lets a platform
 * outage surface as a 5xx or kill the sweep.
 *
 * <p><b>Auto-post dedupe (restart-safe, no in-memory state):</b> an auto row is
 * inserted ARMED ({@code auto_active=1}) for its (deal, platform). Each tick
 * first DISARMS armed rows whose deal is no longer live (window closed,
 * disabled, deleted — derived purely from current deal state), then posts only
 * where no armed row exists. A restart mid-window sees the armed row and does
 * not duplicate; a deactivate → re-activate cycle is observed as "not live" on
 * an intermediate tick, disarms the old row, and the re-activation posts a
 * fresh one. Failed auto rows stay armed on purpose — one attempt per
 * activation, retried manually from the admin ledger, never a 1-minute retry
 * storm. Full semantics in {@code docs/adr-social-publishing.md}.
 */
@Service
public class LitemallSocialPostServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(LitemallSocialPostServiceImpl.class);

    private static final int DRAFT_SCAN_PAGE_SIZE = 100;

    private final LitemallSocialPostRepository socialPostRepository;
    private final SocialCatalogPort socialCatalogPort;
    private final SocialProperties properties;
    private final Map<LitemallSocialPlatform, SocialPublishPort> publishPorts;

    public LitemallSocialPostServiceImpl(LitemallSocialPostRepository socialPostRepository,
                                         SocialCatalogPort socialCatalogPort,
                                         SocialProperties properties,
                                         List<SocialPublishPort> publishPorts) {
        this.socialPostRepository = socialPostRepository;
        this.socialCatalogPort = socialCatalogPort;
        this.properties = properties;
        this.publishPorts = new EnumMap<>(LitemallSocialPlatform.class);
        for (SocialPublishPort port : publishPorts) {
            this.publishPorts.put(port.platform(), port);
        }
    }

    // ------------------------------------------------------------------
    // Composer
    // ------------------------------------------------------------------

    /** Everything the admin composer dialog needs; empty when the goods does not exist. */
    public Optional<ComposePreview> composePreview(Integer goodsId) {
        Optional<GoodsSocialSnapshot> maybeSnapshot = socialCatalogPort.goodsSnapshot(goodsId);
        if (maybeSnapshot.isEmpty()) {
            return Optional.empty();
        }
        GoodsSocialSnapshot snapshot = maybeSnapshot.get();
        LiveDeal deal = socialCatalogPort.liveDealFor(goodsId).orElse(null);
        String slug = campaignSlug(goodsId, deal);

        Set<String> images = new LinkedHashSet<>();
        if (StringUtils.hasText(snapshot.picUrl())) {
            images.add(snapshot.picUrl());
        }
        images.addAll(snapshot.gallery());

        List<PlatformAvailability> platforms = new ArrayList<>();
        for (LitemallSocialPlatform platform : LitemallSocialPlatform.values()) {
            SocialPublishPort port = publishPorts.get(platform);
            boolean enabled = port != null && port.isEnabled();
            boolean available;
            String reason = null;
            switch (platform) {
                case META_IG -> {
                    available = !images.isEmpty();
                    if (!available) {
                        reason = "Instagram requires an image — this goods has none";
                    }
                }
                case TIKTOK -> {
                    available = StringUtils.hasText(snapshot.videoUrl());
                    if (!available) {
                        reason = "TikTok requires a video — this goods has none";
                    }
                }
                default -> available = true;
            }
            platforms.add(new PlatformAvailability(platform, enabled, available, reason,
                    UtmShareLink.productUrl(properties.getShareBaseUrl(), goodsId, platform, slug)));
        }

        return Optional.of(new ComposePreview(
                goodsId,
                snapshot.name(),
                snapshot.retailPrice(),
                deal != null ? deal.dealPrice() : null,
                deal != null ? deal.dealId() : null,
                captionFor(snapshot, deal),
                new ArrayList<>(images),
                snapshot.videoUrl(),
                platforms));
    }

    /**
     * Manual composer post: one ledger row per requested platform, publishing
     * what it can — a disabled adapter or per-platform failure becomes a failed
     * row and an honest per-platform outcome, never an exception.
     * Empty when the goods does not exist.
     */
    public Optional<List<PublishOutcome>> post(Integer goodsId, String caption, String mediaUrl,
                                               List<LitemallSocialPlatform> platforms, String postedBy) {
        Optional<GoodsSocialSnapshot> maybeSnapshot = socialCatalogPort.goodsSnapshot(goodsId);
        if (maybeSnapshot.isEmpty()) {
            return Optional.empty();
        }
        GoodsSocialSnapshot snapshot = maybeSnapshot.get();
        LiveDeal deal = socialCatalogPort.liveDealFor(goodsId).orElse(null);
        String slug = campaignSlug(goodsId, deal);
        String effectiveCaption = StringUtils.hasText(caption) ? caption : captionFor(snapshot, deal);

        List<PublishOutcome> outcomes = new ArrayList<>();
        for (LitemallSocialPlatform platform : new LinkedHashSet<>(platforms)) {
            LitemallSocialPostAggregate row = new LitemallSocialPostAggregate();
            row.setGoodsId(goodsId);
            row.setPlatform(platform);
            row.setCaption(effectiveCaption);
            // TikTok publishes the goods' video; the composer's media pick is an image.
            row.setMediaUrl(platform == LitemallSocialPlatform.TIKTOK ? snapshot.videoUrl() : mediaUrl);
            row.setLinkUrl(UtmShareLink.productUrl(properties.getShareBaseUrl(), goodsId, platform, slug));
            row.setStatus(LitemallSocialPostStatus.DRAFT);
            row.setPostedBy(StringUtils.hasText(postedBy) ? postedBy : "");
            row.setDealId(deal != null ? deal.dealId() : null);
            row.setAutoActive(false);
            socialPostRepository.insert(row);
            outcomes.add(publishAndRecord(row));
        }
        return Optional.of(outcomes);
    }

    /** Re-fire a FAILED row (only) with its stored caption/media/link. */
    public RetryResult retry(Integer id) {
        Optional<LitemallSocialPostAggregate> maybeRow = socialPostRepository.findById(id);
        if (maybeRow.isEmpty()) {
            return new RetryResult(false, false, null);
        }
        LitemallSocialPostAggregate row = maybeRow.get();
        if (!row.isRetryable()) {
            return new RetryResult(true, false, null);
        }
        return new RetryResult(true, true, publishAndRecord(row));
    }

    public PostPage list(int page, int limit, LitemallSocialPostStatus status,
                         LitemallSocialPlatform platform) {
        return new PostPage(
                socialPostRepository.count(status, platform),
                socialPostRepository.page(status, platform, page, limit));
    }

    // ------------------------------------------------------------------
    // Flash-deal auto-poster (called by SocialDealAutoPoster's @Scheduled tick)
    // ------------------------------------------------------------------

    public void autoPostTick() {
        if (!properties.isAutoPostDeals()) {
            return;
        }
        List<LiveDeal> liveDeals = socialCatalogPort.liveDeals();
        Set<Integer> liveDealIds = new HashSet<>();
        for (LiveDeal deal : liveDeals) {
            liveDealIds.add(deal.dealId());
        }

        // 1. Disarm: armed rows whose deal is no longer live ended their activation;
        //    a later re-activation of the same deal then posts a fresh row.
        Set<String> armedKeys = new HashSet<>();
        for (LitemallSocialPostAggregate armed : socialPostRepository.findAutoArmed()) {
            if (armed.getDealId() == null || !liveDealIds.contains(armed.getDealId())) {
                socialPostRepository.disarm(armed.getId());
            } else {
                armedKeys.add(dedupeKey(armed.getDealId(), armed.getPlatform()));
            }
        }

        // 2. Post: live deals × enabled platforms with no armed row yet.
        for (LiveDeal deal : liveDeals) {
            GoodsSocialSnapshot snapshot = null;
            for (SocialPublishPort port : publishPorts.values()) {
                if (!port.isEnabled()) {
                    continue;
                }
                LitemallSocialPlatform platform = port.platform();
                if (armedKeys.contains(dedupeKey(deal.dealId(), platform))) {
                    continue;
                }
                if (snapshot == null) {
                    snapshot = socialCatalogPort.goodsSnapshot(deal.goodsId()).orElse(null);
                    if (snapshot == null) {
                        logger.warn("auto-post: live deal {} references missing goods {} — skipping",
                                deal.dealId(), deal.goodsId());
                        break;
                    }
                }
                LitemallSocialPostAggregate row = new LitemallSocialPostAggregate();
                row.setGoodsId(deal.goodsId());
                row.setPlatform(platform);
                row.setCaption(captionFor(snapshot, deal));
                row.setMediaUrl(platform == LitemallSocialPlatform.TIKTOK
                        ? snapshot.videoUrl() : snapshot.picUrl());
                row.setLinkUrl(UtmShareLink.productUrl(
                        properties.getShareBaseUrl(), deal.goodsId(), platform,
                        UtmShareLink.dealSlug(deal.dealId())));
                row.setStatus(LitemallSocialPostStatus.DRAFT);
                row.setPostedBy(LitemallSocialPostAggregate.POSTED_BY_AUTO);
                row.setDealId(deal.dealId());
                row.setAutoActive(true);
                socialPostRepository.insert(row);
                PublishOutcome outcome = publishAndRecord(row);
                logger.info("auto-post: deal {} goods {} → {} = {}{}",
                        deal.dealId(), deal.goodsId(), platform.getDbValue(),
                        outcome.status().getDbValue(),
                        outcome.error() != null ? " (" + outcome.error() + ")" : "");
            }
        }
    }

    // ------------------------------------------------------------------
    // Wave-12 scheduled category campaigns (called via LitemallCampaignSchedulingServiceImpl)
    // ------------------------------------------------------------------

    /**
     * Create UNPUBLISHED draft rows for a scheduled campaign: one per
     * (goods × platform), correlated to the campaign by the
     * {@code utm_campaign=campaign-<id>} slug in {@code link_url} (the V42
     * ledger has no campaign column). {@link #publishCampaignDrafts(Integer)}
     * fires them when the campaign activates. Goods that no longer exist are
     * skipped with a WARN — never an exception.
     */
    public List<CampaignDraft> createCampaignDrafts(Integer campaignId, List<Integer> goodsIds,
                                                    List<LitemallSocialPlatform> platforms, String postedBy) {
        String slug = UtmShareLink.campaignSlug(campaignId);
        List<CampaignDraft> drafts = new ArrayList<>();
        for (Integer goodsId : new LinkedHashSet<>(goodsIds)) {
            GoodsSocialSnapshot snapshot = socialCatalogPort.goodsSnapshot(goodsId).orElse(null);
            if (snapshot == null) {
                logger.warn("campaign {}: goods {} not found — skipping its drafts", campaignId, goodsId);
                continue;
            }
            LiveDeal deal = socialCatalogPort.liveDealFor(goodsId).orElse(null);
            for (LitemallSocialPlatform platform : new LinkedHashSet<>(platforms)) {
                LitemallSocialPostAggregate row = new LitemallSocialPostAggregate();
                row.setGoodsId(goodsId);
                row.setPlatform(platform);
                row.setCaption(captionFor(snapshot, deal));
                row.setMediaUrl(platform == LitemallSocialPlatform.TIKTOK
                        ? snapshot.videoUrl() : snapshot.picUrl());
                row.setLinkUrl(UtmShareLink.productUrl(
                        properties.getShareBaseUrl(), goodsId, platform, slug));
                row.setStatus(LitemallSocialPostStatus.DRAFT);
                row.setPostedBy(StringUtils.hasText(postedBy) ? postedBy : "");
                row.setDealId(deal != null ? deal.dealId() : null);
                row.setAutoActive(false);
                socialPostRepository.insert(row);
                drafts.add(new CampaignDraft(row.getId(), goodsId, platform));
            }
        }
        return drafts;
    }

    /**
     * Publish every remaining draft of a campaign (matched by its UTM slug).
     * Fail-soft per row — a disabled adapter or API error becomes an honest
     * {@code failed} row (retryable from the admin ledger), never an exception.
     */
    public List<PublishOutcome> publishCampaignDrafts(Integer campaignId) {
        String marker = "utm_campaign=" + UtmShareLink.campaignSlug(campaignId);
        // Collect first, then publish: publishAndRecord moves rows out of DRAFT,
        // which would shift pages under an interleaved scan.
        List<LitemallSocialPostAggregate> targets = new ArrayList<>();
        for (int page = 1; ; page++) {
            List<LitemallSocialPostAggregate> batch =
                    socialPostRepository.page(LitemallSocialPostStatus.DRAFT, null, page, DRAFT_SCAN_PAGE_SIZE);
            for (LitemallSocialPostAggregate row : batch) {
                if (row.getLinkUrl() != null && row.getLinkUrl().endsWith(marker)) {
                    targets.add(row);
                }
            }
            if (batch.size() < DRAFT_SCAN_PAGE_SIZE) {
                break;
            }
        }
        List<PublishOutcome> outcomes = new ArrayList<>();
        for (LitemallSocialPostAggregate row : targets) {
            PublishOutcome outcome = publishAndRecord(row);
            outcomes.add(outcome);
            logger.info("campaign {}: goods {} → {} = {}{}",
                    campaignId, row.getGoodsId(), row.getPlatform().getDbValue(),
                    outcome.status().getDbValue(),
                    outcome.error() != null ? " (" + outcome.error() + ")" : "");
        }
        return outcomes;
    }

    /** Whether the platform's publish adapter is present and enabled (for honest composer responses). */
    public boolean isPlatformEnabled(LitemallSocialPlatform platform) {
        SocialPublishPort port = publishPorts.get(platform);
        return port != null && port.isEnabled();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private PublishOutcome publishAndRecord(LitemallSocialPostAggregate row) {
        SocialPublishPort port = publishPorts.get(row.getPlatform());
        SocialPublishResult result;
        if (port == null) {
            result = SocialPublishResult.fail("no adapter registered for " + row.getPlatform());
        } else {
            boolean isVideoPlatform = row.getPlatform() == LitemallSocialPlatform.TIKTOK;
            result = port.publish(new SocialPublishCommand(
                    row.getGoodsId(),
                    row.getCaption(),
                    isVideoPlatform ? null : row.getMediaUrl(),
                    isVideoPlatform ? row.getMediaUrl() : null,
                    row.getLinkUrl()));
        }
        if (result.success()) {
            socialPostRepository.markPosted(row.getId(), result.externalPostId());
            return new PublishOutcome(row.getId(), row.getPlatform(),
                    LitemallSocialPostStatus.POSTED, result.externalPostId(), null);
        }
        socialPostRepository.markFailed(row.getId(), result.error());
        logger.warn("social publish failed (row {}, {}): {}",
                row.getId(), row.getPlatform().getDbValue(), result.error());
        return new PublishOutcome(row.getId(), row.getPlatform(),
                LitemallSocialPostStatus.FAILED, null, result.error());
    }

    /** English plain-text caption template; the composer lets the admin edit it before posting. */
    private String captionFor(GoodsSocialSnapshot snapshot, LiveDeal deal) {
        if (deal != null) {
            String was = deal.originalPrice() != null
                    ? " (was $" + plain(deal.originalPrice()) + ")"
                    : "";
            return "Flash deal: " + snapshot.name() + " — now $" + plain(deal.dealPrice())
                    + was + ". Limited time only!";
        }
        return snapshot.name() + " — $" + plain(snapshot.retailPrice()) + ". Shop now!";
    }

    private String campaignSlug(Integer goodsId, LiveDeal deal) {
        return deal != null ? UtmShareLink.dealSlug(deal.dealId()) : UtmShareLink.goodsSlug(goodsId);
    }

    private static String dedupeKey(Integer dealId, LitemallSocialPlatform platform) {
        return dealId + ":" + platform.getDbValue();
    }

    private static String plain(BigDecimal price) {
        return price != null ? price.toPlainString() : "?";
    }

    // ------------------------------------------------------------------
    // Result types (mapped to DTOs at the interfaces layer)
    // ------------------------------------------------------------------

    public record PlatformAvailability(LitemallSocialPlatform platform, boolean enabled,
                                       boolean available, String reason, String shareUrl) {
    }

    public record ComposePreview(Integer goodsId, String goodsName, BigDecimal price,
                                 BigDecimal dealPrice, Integer dealId, String caption,
                                 List<String> images, String videoUrl,
                                 List<PlatformAvailability> platforms) {
    }

    public record PublishOutcome(Integer postId, LitemallSocialPlatform platform,
                                 LitemallSocialPostStatus status, String externalPostId,
                                 String error) {
    }

    /** @param retried false with found=true means the row was not in FAILED (retry guard). */
    public record RetryResult(boolean found, boolean retried, PublishOutcome outcome) {
    }

    /** One unpublished campaign draft row (Wave-12 from-category composer). */
    public record CampaignDraft(Integer postId, Integer goodsId, LitemallSocialPlatform platform) {
    }

    public record PostPage(int total, List<LitemallSocialPostAggregate> rows) {
    }
}
