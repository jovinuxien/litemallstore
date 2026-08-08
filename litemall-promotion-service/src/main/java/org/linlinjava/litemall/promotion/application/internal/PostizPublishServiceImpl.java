package org.linlinjava.litemall.promotion.application.internal;

import org.linlinjava.litemall.db.domain.LitemallPostizPost;
import org.linlinjava.litemall.promotion.application.ports.PostizGatewayException;
import org.linlinjava.litemall.promotion.application.ports.PostizPort;
import org.linlinjava.litemall.promotion.application.ports.PostizPort.PostizChannel;
import org.linlinjava.litemall.promotion.application.ports.PromoPagePort;
import org.linlinjava.litemall.promotion.application.ports.PromoPagePort.PageComponent;
import org.linlinjava.litemall.promotion.application.ports.PromoPagePort.PromoPage;
import org.linlinjava.litemall.promotion.application.ports.SocialCatalogPort;
import org.linlinjava.litemall.promotion.application.ports.SocialCatalogPort.GoodsSocialSnapshot;
import org.linlinjava.litemall.promotion.application.ports.SocialCatalogPort.LiveDeal;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallPostizPostRepository;
import org.linlinjava.litemall.promotion.infrastructure.configuration.PostizProperties;
import org.linlinjava.litemall.promotion.utils.SeoSlugger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Wave-17 Postiz publishing vertical: composes product posts (name, live-deal
 * or retail price, canonical slugged storefront link, hero image) and hands
 * them to Postiz — one {@code POST /posts} call per product targeting all
 * selected channels, publish instants spread {@code start + i×interval}.
 *
 * <p>Fail-soft end to end: env unconfigured ⇒ {@link PostizRequestException}
 * with the typed "not configured" errno; a Postiz validation 400 fails only
 * that product (its message surfaced verbatim per channel, one failed ledger
 * row each); products whose image lacks a real media extension are SKIPPED
 * with a warning (Postiz refuses extension-less external URLs; no
 * upload-from-url fallback in v1).
 */
@Service
public class PostizPublishServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(PostizPublishServiceImpl.class);

    public static final int ERRNO_NOT_CONFIGURED = 760;
    public static final int ERRNO_BATCH_TOO_LARGE = 761;
    public static final int ERRNO_POSTIZ_UNREACHABLE = 762;
    public static final int ERRNO_UNKNOWN_TARGET = 763;
    /** Wave-20 DIY-page source: page missing / draft / deactivated (goods errno 642 upstream). */
    public static final int ERRNO_PAGE_NOT_ACTIVE = 764;
    // 765 (groupon-page hold) was RETIRED in Wave 21: priced groupon submit
    // shipped, so groupon-category pages are publishable like any other.
    /** goods-management page read unreachable / unexpected envelope. */
    public static final int ERRNO_PAGE_SOURCE_UNAVAILABLE = 766;
    public static final int ERRNO_BAD_PARAM = 402;

    /** Extensions Postiz accepts on an external image URL (query string ignored). */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp");

    /**
     * Providers the composer can satisfy required settings for. Postiz demands a
     * settings object per post even for drafts; anything not in this map renders
     * {@code supported:false} in {@code /channels}.
     */
    private static final Map<String, Map<String, Object>> SUPPORTED_SETTINGS = Map.of(
            "facebook", Map.of(),
            "instagram", Map.of("post_type", "post"),
            "instagram-standalone", Map.of("post_type", "post"),
            "x", Map.of("who_can_reply_post", "everyone"),
            "threads", Map.of(),
            "mastodon", Map.of(),
            "bluesky", Map.of(),
            "telegram", Map.of(),
            "nostr", Map.of(),
            "vk", Map.of());

    private static final DateTimeFormatter UTC_ISO = DateTimeFormatter.ISO_INSTANT;

    private final PostizPort postizPort;
    private final PostizProperties properties;
    private final SocialCatalogPort catalogPort;
    private final PromoPagePort pagePort;
    private final LitemallPostizPostRepository repository;

    /** Channel-list cache — Postiz's shipped compose throttles the public API at 30 calls/h. */
    private volatile CachedChannels cachedChannels;

    public PostizPublishServiceImpl(PostizPort postizPort,
                                    PostizProperties properties,
                                    SocialCatalogPort catalogPort,
                                    PromoPagePort pagePort,
                                    LitemallPostizPostRepository repository) {
        this.postizPort = postizPort;
        this.properties = properties;
        this.catalogPort = catalogPort;
        this.pagePort = pagePort;
        this.repository = repository;
    }

    // ------------------------------------------------------------------
    // Status + channels
    // ------------------------------------------------------------------

    /** UI visibility switch: {@code channelCount} is null when Postiz can't be reached right now. */
    public Status status() {
        if (!properties.isConfigured()) {
            return new Status(false, null);
        }
        try {
            return new Status(true, channels().size());
        } catch (PostizGatewayException e) {
            logger.warn("Postiz status probe failed: {}", e.getMessage());
            return new Status(true, null);
        }
    }

    /** Connected Postiz channels with the composer's supported/reason verdict. */
    public List<ChannelView> channelViews() {
        requireConfigured();
        List<ChannelView> views = new ArrayList<>();
        for (PostizChannel channel : channels()) {
            String reason = null;
            boolean supported = true;
            if (channel.disabled()) {
                supported = false;
                reason = "channel is disabled in Postiz (reconnect it there)";
            } else if (!SUPPORTED_SETTINGS.containsKey(channel.identifier())) {
                supported = false;
                reason = "provider '" + channel.identifier()
                        + "' requires settings this composer does not support yet";
            }
            views.add(new ChannelView(channel.integrationId(), channel.identifier(),
                    channel.name(), channel.picture(), supported, reason));
        }
        return views;
    }

    // ------------------------------------------------------------------
    // Preview + publish
    // ------------------------------------------------------------------

    /** Composes the batch with ZERO side effects — what {@code /publish} would send. */
    public BatchPreview preview(BatchRequest request) {
        requireConfigured();
        Composition composition = compose(request);
        List<ProductPreview> batch = new ArrayList<>();
        for (ComposedProduct product : composition.products()) {
            List<PerChannel> perChannel = new ArrayList<>();
            if (!product.skipped()) {
                for (ResolvedChannel channel : composition.channels()) {
                    perChannel.add(new PerChannel(channel.integrationId(), product.content(),
                            channel.settings()));
                }
            }
            batch.add(new ProductPreview(product.goodsId(), product.name(), product.picUrl(),
                    product.scheduleAtIso(), product.warnings(), perChannel));
        }
        return new BatchPreview(batch, composition.batchWarnings());
    }

    /** Same composition as {@link #preview}; one Postiz call + ledger rows per product. */
    public List<ProductResult> publish(BatchRequest request, String adminId) {
        requireConfigured();
        Composition composition = compose(request);
        List<ProductResult> results = new ArrayList<>();
        for (ComposedProduct product : composition.products()) {
            if (product.skipped()) {
                // Nothing was handed to Postiz — visible in the response, no ledger row.
                List<ChannelOutcome> outcomes = composition.channels().stream()
                        .map(c -> new ChannelOutcome(c.integrationId(), false, null,
                                "skipped: " + String.join("; ", product.warnings())))
                        .toList();
                results.add(new ProductResult(product.goodsId(), null, outcomes));
                continue;
            }
            results.add(publishOne(product, composition.channels(), request.categoryId(), adminId));
        }
        return results;
    }

    private ProductResult publishOne(ComposedProduct product, List<ResolvedChannel> channels,
                                     Integer categoryId, String adminId) {
        List<ChannelOutcome> outcomes = new ArrayList<>();
        Map<String, String> postIdByIntegration = new LinkedHashMap<>();
        String failure = null;
        try {
            List<PostizPort.ChannelPost> targets = channels.stream()
                    .map(c -> new PostizPort.ChannelPost(c.integrationId(), product.content(),
                            product.imageUrl(), c.settings()))
                    .toList();
            for (PostizPort.ScheduledPost scheduled : postizPort.schedulePost(product.scheduleAtIso(), targets)) {
                postIdByIntegration.put(scheduled.integrationId(), scheduled.postizPostId());
            }
        } catch (PostizGatewayException e) {
            // One call targets every channel, so a rejection fails the whole product;
            // Postiz's own wording travels verbatim, prefixed with the provider it named.
            failure = (e.getProvider() != null ? e.getProvider() + ": " : "") + e.getMessage();
            logger.warn("Postiz publish failed for goods {}: {}", product.goodsId(), failure);
        }
        for (ResolvedChannel channel : channels) {
            String postizPostId = postIdByIntegration.get(channel.integrationId());
            boolean ok = failure == null && postizPostId != null;
            String error = failure != null ? failure
                    : (postizPostId == null ? "Postiz accepted the call but returned no post id" : null);
            outcomes.add(new ChannelOutcome(channel.integrationId(), ok, postizPostId, error));

            LitemallPostizPost row = new LitemallPostizPost();
            row.setGoodsId(product.goodsId());
            row.setCategoryId(categoryId);
            row.setIntegrationId(channel.integrationId());
            row.setChannelIdentifier(channel.identifier());
            row.setPostizPostId(postizPostId);
            row.setScheduleTime(LocalDateTime.ofInstant(product.scheduleAt(), ZoneOffset.UTC));
            row.setStatus(ok ? "scheduled" : "failed");
            row.setError(error != null && error.length() > 511 ? error.substring(0, 511) : error);
            row.setPostedBy(adminId != null ? adminId : "admin");
            repository.insert(row);
        }
        return new ProductResult(product.goodsId(), product.scheduleAtIso(), outcomes);
    }

    // ------------------------------------------------------------------
    // Page source (Wave 20 — DIY promo pages)
    // ------------------------------------------------------------------

    /** Page-source preview: what {@link #publishPage} would send, ZERO side effects. */
    public PagePreview previewPage(PageRequest request) {
        requireConfigured();
        ComposedPage composed = composePage(request);
        List<PerChannel> perChannel = new ArrayList<>();
        for (ResolvedChannel channel : composed.channels()) {
            perChannel.add(new PerChannel(channel.integrationId(), composed.content(),
                    channel.settings()));
        }
        return new PagePreview(composed.pageId(), composed.name(), composed.imageUrl(),
                composed.scheduleAtIso(), composed.warnings(), perChannel);
    }

    /** Same composition as {@link #previewPage}; ONE Postiz call targeting all channels. */
    public PageResult publishPage(PageRequest request, String adminId) {
        requireConfigured();
        ComposedPage composed = composePage(request);
        List<ChannelOutcome> outcomes = new ArrayList<>();
        Map<String, String> postIdByIntegration = new LinkedHashMap<>();
        String failure = null;
        try {
            List<PostizPort.ChannelPost> targets = composed.channels().stream()
                    .map(c -> new PostizPort.ChannelPost(c.integrationId(), composed.content(),
                            composed.imageUrl(), c.settings()))
                    .toList();
            for (PostizPort.ScheduledPost scheduled : postizPort.schedulePost(composed.scheduleAtIso(), targets)) {
                postIdByIntegration.put(scheduled.integrationId(), scheduled.postizPostId());
            }
        } catch (PostizGatewayException e) {
            failure = (e.getProvider() != null ? e.getProvider() + ": " : "") + e.getMessage();
            logger.warn("Postiz publish failed for page {}: {}", composed.pageId(), failure);
        }
        for (ResolvedChannel channel : composed.channels()) {
            String postizPostId = postIdByIntegration.get(channel.integrationId());
            boolean ok = failure == null && postizPostId != null;
            String error = failure != null ? failure
                    : (postizPostId == null ? "Postiz accepted the call but returned no post id" : null);
            outcomes.add(new ChannelOutcome(channel.integrationId(), ok, postizPostId, error));

            LitemallPostizPost row = new LitemallPostizPost();
            row.setGoodsId(null);
            row.setPageId(composed.pageId());
            row.setCategoryId(null);
            row.setIntegrationId(channel.integrationId());
            row.setChannelIdentifier(channel.identifier());
            row.setPostizPostId(postizPostId);
            row.setScheduleTime(LocalDateTime.ofInstant(composed.scheduleAt(), ZoneOffset.UTC));
            row.setStatus(ok ? "scheduled" : "failed");
            row.setError(error != null && error.length() > 511 ? error.substring(0, 511) : error);
            row.setPostedBy(adminId != null ? adminId : "admin");
            repository.insert(row);
        }
        return new PageResult(composed.pageId(), composed.scheduleAtIso(), outcomes);
    }

    private ComposedPage composePage(PageRequest request) {
        if (request.pageId() == null) {
            throw new PostizRequestException(ERRNO_BAD_PARAM, "pageId is required");
        }
        if (request.channelIds() == null || request.channelIds().isEmpty()) {
            throw new PostizRequestException(ERRNO_BAD_PARAM, "channelIds is required");
        }
        Instant start = parseStart(request.startTime());

        PromoPage page;
        try {
            page = pagePort.fetchActive(request.pageId())
                    .orElseThrow(() -> new PostizRequestException(ERRNO_PAGE_NOT_ACTIVE,
                            "page " + request.pageId()
                                    + " is not active — activate it before publishing"));
        } catch (PromoPagePort.PromoPageGatewayException e) {
            throw new PostizRequestException(ERRNO_PAGE_SOURCE_UNAVAILABLE,
                    "page source unavailable: " + e.getMessage());
        }
        List<ResolvedChannel> channels = resolveChannels(request.channelIds());
        List<String> warnings = new ArrayList<>();
        if (start.isBefore(Instant.now())) {
            warnings.add("start time is in the past — Postiz may reject or publish immediately");
        }

        // Hero image: first image-bearing component; a page without one still
        // publishes (Postiz accepts text-only posts), unlike the goods path.
        String imageUrl = absolutize(heroImage(page.components()));
        if (imageUrl == null) {
            warnings.add("no image with a supported extension (.png/.jpg/.jpeg/.gif/.webp)"
                    + " found on the page — publishing text-only");
        }
        return new ComposedPage(page.id(), page.name(), imageUrl, pageContent(page), start,
                channels, warnings);
    }

    /**
     * Sanitizer-safe HTML for a page post: page name as heading, a short
     * category-aware line, canonical storefront link.
     */
    private String pageContent(PromoPage page) {
        String link = properties.getPublicBaseUrl() + "/page/" + page.id();
        String line;
        if ("coupon".equalsIgnoreCase(page.category())) {
            line = "Coupons and savings inside — claim yours before they're gone.";
        } else if ("groupon".equalsIgnoreCase(page.category())) {
            line = "Team up, unlock the group price — grab a spot before the group fills.";
        } else {
            line = "A hand-picked collection, fresh on Trovemo.";
        }
        return "<h2>" + escapeHtml(page.name()) + "</h2>"
                + "<p>" + escapeHtml(line) + "</p>"
                + "<p><a href=\"" + link + "\">See the page on Trovemo</a></p>";
    }

    /**
     * First image candidate across the page's components, in render order.
     * Component configs vary per type (banner {@code imageUrl}, image-row
     * {@code images[]}, goods-list none), so the scan is defensive: any string
     * under a key named {@code imageUrl}/{@code image}/{@code src} — or a bare
     * string inside a list — counts when it ends in a real image extension.
     */
    private String heroImage(List<PageComponent> components) {
        if (components == null) {
            return null;
        }
        for (PageComponent component : components) {
            String candidate = imageCandidate(component.config());
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static final List<String> IMAGE_CONFIG_KEYS = List.of("imageUrl", "image", "src");

    private String imageCandidate(Object node) {
        if (node instanceof Map<?, ?> map) {
            for (String key : IMAGE_CONFIG_KEYS) {
                Object value = map.get(key);
                if (value instanceof String s && StringUtils.hasText(s) && hasImageExtension(s)) {
                    return s;
                }
            }
            for (Object value : map.values()) {
                if (value instanceof Map || value instanceof List) {
                    String found = imageCandidate(value);
                    if (found != null) {
                        return found;
                    }
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s && StringUtils.hasText(s) && hasImageExtension(s)) {
                    return s;
                }
                if (item instanceof Map || item instanceof List) {
                    String found = imageCandidate(item);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Log
    // ------------------------------------------------------------------

    public LitemallPostizPostRepository.PostizPostPage log(int page, int limit) {
        requireConfigured();
        return repository.page(page, limit);
    }

    // ------------------------------------------------------------------
    // Composition
    // ------------------------------------------------------------------

    private Composition compose(BatchRequest request) {
        if (request.goodsIds() == null || request.goodsIds().isEmpty()) {
            throw new PostizRequestException(ERRNO_BAD_PARAM, "goodsIds is required");
        }
        if (request.channelIds() == null || request.channelIds().isEmpty()) {
            throw new PostizRequestException(ERRNO_BAD_PARAM, "channelIds is required");
        }
        if (request.intervalMinutes() == null || request.intervalMinutes() < 0) {
            throw new PostizRequestException(ERRNO_BAD_PARAM, "intervalMinutes must be >= 0");
        }
        if (request.goodsIds().size() > properties.getBatchCap()) {
            throw new PostizRequestException(ERRNO_BATCH_TOO_LARGE,
                    "batch too large: " + request.goodsIds().size() + " products, max "
                            + properties.getBatchCap() + " per publish (Postiz throttles its API hourly)");
        }
        Instant start = parseStart(request.startTime());
        List<ResolvedChannel> channels = resolveChannels(request.channelIds());

        List<String> batchWarnings = new ArrayList<>();
        if (start.isBefore(Instant.now())) {
            batchWarnings.add("start time is in the past — Postiz may reject or publish immediately");
        }

        Map<Integer, LocalDateTime> lastScheduled = lastScheduledByGoods(request.goodsIds());
        List<ComposedProduct> products = new ArrayList<>();
        int scheduleIndex = 0;
        for (Integer goodsId : request.goodsIds()) {
            GoodsSocialSnapshot snapshot = catalogPort.goodsSnapshot(goodsId)
                    .orElseThrow(() -> new PostizRequestException(ERRNO_UNKNOWN_TARGET,
                            "unknown goods " + goodsId));
            List<String> warnings = new ArrayList<>();
            dedupWarning(lastScheduled.get(goodsId)).ifPresent(warnings::add);

            String imageUrl = absolutize(snapshot.picUrl());
            boolean skipped = false;
            if (!StringUtils.hasText(imageUrl)) {
                skipped = true;
                warnings.add("no product image — product skipped");
            } else if (!hasImageExtension(imageUrl)) {
                skipped = true;
                warnings.add("image URL lacks a supported extension (.png/.jpg/.jpeg/.gif/.webp) — product skipped");
            }

            Instant scheduleAt = null;
            String content = null;
            if (!skipped) {
                scheduleAt = start.plus(Duration.ofMinutes(
                        (long) request.intervalMinutes() * scheduleIndex++));
                content = contentFor(snapshot);
            }
            products.add(new ComposedProduct(goodsId, snapshot.name(), snapshot.picUrl(), imageUrl,
                    content, scheduleAt, skipped, warnings));
        }
        return new Composition(products, channels, batchWarnings);
    }

    /**
     * Sanitizer-safe HTML (Postiz allows {@code p/strong/a} among others): name,
     * live flash-deal price when a price swap is active (else retail), canonical
     * slugged storefront link.
     */
    private String contentFor(GoodsSocialSnapshot snapshot) {
        Optional<LiveDeal> deal = catalogPort.liveDealFor(snapshot.goodsId());
        String link = properties.getPublicBaseUrl()
                + SeoSlugger.productPath(snapshot.goodsId(), snapshot.name());
        StringBuilder html = new StringBuilder();
        html.append("<p><strong>").append(escapeHtml(snapshot.name())).append("</strong></p>");
        if (deal.isPresent()) {
            html.append("<p>Deal price ").append(money(deal.get().dealPrice()));
            BigDecimal was = deal.get().originalPrice();
            if (was != null) {
                html.append(" — was ").append(money(was));
            }
            html.append("</p>");
        } else {
            html.append("<p>").append(money(snapshot.retailPrice())).append("</p>");
        }
        html.append("<p><a href=\"").append(link).append("\">Shop now on Trovemo</a></p>");
        return html.toString();
    }

    private List<ResolvedChannel> resolveChannels(List<String> channelIds) {
        Map<String, PostizChannel> byId = new LinkedHashMap<>();
        for (PostizChannel channel : channels()) {
            byId.put(channel.integrationId(), channel);
        }
        List<ResolvedChannel> resolved = new ArrayList<>();
        for (String channelId : channelIds) {
            PostizChannel channel = byId.get(channelId);
            if (channel == null) {
                throw new PostizRequestException(ERRNO_UNKNOWN_TARGET,
                        "unknown channel " + channelId + " (not a connected Postiz integration)");
            }
            if (channel.disabled()) {
                throw new PostizRequestException(ERRNO_UNKNOWN_TARGET,
                        "channel " + channel.name() + " is disabled in Postiz");
            }
            Map<String, Object> settings = SUPPORTED_SETTINGS.get(channel.identifier());
            if (settings == null) {
                throw new PostizRequestException(ERRNO_UNKNOWN_TARGET,
                        "channel " + channel.name() + " (" + channel.identifier()
                                + ") is not supported by this composer yet");
            }
            resolved.add(new ResolvedChannel(channel.integrationId(), channel.identifier(), settings));
        }
        return resolved;
    }

    /** Latest UTC schedule instant per goods inside the dedup look-back window. */
    private Map<Integer, LocalDateTime> lastScheduledByGoods(List<Integer> goodsIds) {
        LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC)
                .minusDays(properties.getDedupWarnDays());
        Map<Integer, LocalDateTime> latest = new LinkedHashMap<>();
        for (LitemallPostizPost row : repository.scheduledSince(goodsIds, since)) {
            if (row.getScheduleTime() == null) {
                continue;
            }
            latest.merge(row.getGoodsId(), row.getScheduleTime(),
                    (a, b) -> a.isAfter(b) ? a : b);
        }
        return latest;
    }

    private Optional<String> dedupWarning(LocalDateTime lastUtc) {
        if (lastUtc == null) {
            return Optional.empty();
        }
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        if (lastUtc.isAfter(nowUtc)) {
            return Optional.of("already scheduled for " + lastUtc.atOffset(ZoneOffset.UTC).format(UTC_ISO));
        }
        long days = Duration.between(lastUtc, nowUtc).toDays();
        return Optional.of(days == 0 ? "posted today" : "posted " + days + " day" + (days == 1 ? "" : "s") + " ago");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new PostizRequestException(ERRNO_NOT_CONFIGURED,
                    "Postiz publishing is not configured (set LITEMALL_POSTIZ_BASE_URL and LITEMALL_POSTIZ_API_KEY)");
        }
    }

    private List<PostizChannel> channels() {
        CachedChannels cached = cachedChannels;
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAt() < properties.getChannelsCacheTtlMs()) {
            return cached.list();
        }
        List<PostizChannel> fresh = postizPort.channels();
        cachedChannels = new CachedChannels(fresh, now);
        return fresh;
    }

    private Instant parseStart(String startTime) {
        if (!StringUtils.hasText(startTime)) {
            throw new PostizRequestException(ERRNO_BAD_PARAM, "startTime is required (ISO-8601)");
        }
        try {
            return OffsetDateTime.parse(startTime).toInstant();
        } catch (DateTimeParseException ignored) {
            // No offset — the contract reads offset-less timestamps as UTC.
        }
        try {
            return LocalDateTime.parse(startTime).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new PostizRequestException(ERRNO_BAD_PARAM,
                    "startTime must be ISO-8601 (e.g. 2026-08-05T14:00:00Z)");
        }
    }

    private String absolutize(String picUrl) {
        if (!StringUtils.hasText(picUrl)) {
            return null;
        }
        if (picUrl.startsWith("http://") || picUrl.startsWith("https://")) {
            return picUrl;
        }
        String base = properties.getPublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return picUrl.startsWith("/") ? base + picUrl : base + "/" + picUrl;
    }

    /** Postiz's own rule: extension check on the path with the query string stripped. */
    private boolean hasImageExtension(String url) {
        String path = url.split("\\?")[0].toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    private String money(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------------
    // Shapes
    // ------------------------------------------------------------------

    /** Typed request failure the controller maps onto the errno envelope. */
    public static class PostizRequestException extends RuntimeException {
        private final int errno;

        public PostizRequestException(int errno, String message) {
            super(message);
            this.errno = errno;
        }

        public int getErrno() {
            return errno;
        }
    }

    public record Status(boolean enabled, Integer channelCount) {
    }

    public record ChannelView(String integrationId, String identifier, String name,
                              String picture, boolean supported, String reason) {
    }

    /** @param intervalMinutes gap between consecutive posts; {@code categoryId} informational */
    public record BatchRequest(List<Integer> goodsIds, List<String> channelIds,
                               String startTime, Integer intervalMinutes, Integer categoryId) {
    }

    public record PerChannel(String integrationId, String content, Map<String, Object> settings) {
    }

    /** @param scheduleAt null when the product was skipped */
    public record ProductPreview(Integer goodsId, String name, String picUrl, String scheduleAt,
                                 List<String> warnings, List<PerChannel> perChannel) {
    }

    public record BatchPreview(List<ProductPreview> batch, List<String> warnings) {
    }

    public record ChannelOutcome(String integrationId, boolean ok, String postizPostId, String error) {
    }

    public record ProductResult(Integer goodsId, String scheduleAt, List<ChannelOutcome> channels) {
    }

    /** Wave-20 page-source body: one post per publish, no interval. */
    public record PageRequest(Integer pageId, List<String> channelIds, String startTime) {
    }

    /** @param picUrl absolutized hero image; null when the page publishes text-only */
    public record PagePreview(Integer pageId, String name, String picUrl, String scheduleAt,
                              List<String> warnings, List<PerChannel> perChannel) {
    }

    public record PageResult(Integer pageId, String scheduleAt, List<ChannelOutcome> channels) {
    }

    private record CachedChannels(List<PostizChannel> list, long fetchedAt) {
    }

    private record ResolvedChannel(String integrationId, String identifier, Map<String, Object> settings) {
    }

    /** @param scheduleAtIso UTC ISO instant sent to Postiz; null when skipped */
    private record ComposedProduct(Integer goodsId, String name, String picUrl, String imageUrl,
                                   String content, Instant scheduleAt, boolean skipped,
                                   List<String> warnings) {
        String scheduleAtIso() {
            return scheduleAt != null ? UTC_ISO.format(scheduleAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)) : null;
        }
    }

    private record Composition(List<ComposedProduct> products, List<ResolvedChannel> channels,
                               List<String> batchWarnings) {
    }

    /** @param imageUrl absolutized hero image; null ⇒ text-only post */
    private record ComposedPage(Integer pageId, String name, String imageUrl, String content,
                                Instant scheduleAt, List<ResolvedChannel> channels,
                                List<String> warnings) {
        String scheduleAtIso() {
            return UTC_ISO.format(scheduleAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        }
    }
}
