package org.linlinjava.litemall.goods.application.comment;

import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.domain.LitemallComment;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCommentService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductComment;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview.CJProductReviewData;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demand-driven CJ review ingest ({@code CjDetailEnrichmentService} precedent, applied to
 * review BODIES): the first {@code /srv/comment} read of a CJ-sourced good fetches its newest
 * CJ product comments once — capped at {@value #MAX_PAGES}×{@value #PAGE_SIZE} — lands them in
 * {@code litemall_comment} with {@code source='cj'}, and stamps
 * {@code litemall_goods.cj_reviews_ingested_time}. Every later read serves purely from the
 * local store (no CJ traffic), merged newest-first with customer-posted rows. No bulk
 * pre-import of the 9k-product catalog ever happens.
 *
 * <p><b>Fail-soft / retryable:</b> with the CJ ACL disabled or the key blank (prod today) the
 * ingest short-circuits BEFORE any CJ attempt and — crucially — leaves the marker NULL, so the
 * same product ingests normally once a key appears. A page-1 fetch failure likewise leaves the
 * marker NULL (retry on a later view, paced by an in-memory per-pid cooldown so an outage is
 * not re-probed on every PDP render); a failure after page 1 stamps what already landed —
 * the cap is best-effort, not a contract.
 *
 * <p><b>Idempotent:</b> rows carry the CJ commentId in {@code external_id} under the
 * {@code uk_comment_source_external} unique key; a concurrent or repeated ingest turns into
 * skipped duplicate inserts. Ranking signals (review_count/rating) are NOT touched here —
 * for CJ goods they are enrichment-owned CJ-side totals, which the capped local subset must
 * not overwrite.
 */
@Service
public class CjReviewIngestService {

    private static final Logger logger = LoggerFactory.getLogger(CjReviewIngestService.class);

    /** CJ productComments page size; 3 pages ≈ the 60 newest reviews, ~3 s worst case at 1 QPS. */
    static final int PAGE_SIZE = 20;
    static final int MAX_PAGES = 3;

    /** litemall_comment column widths (V44 / original schema). */
    private static final int MAX_CONTENT = 1023;
    private static final int MAX_AUTHOR_NAME = 64;
    private static final int MAX_AUTHOR_AVATAR = 255;
    private static final int MAX_PIC_URLS_JSON = 1023;

    private static final DateTimeFormatter CJ_SPACE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CJDropshippingConfig config;
    private final CJProductService cjProductService;
    private final LitemallCommentService commentService;
    private final LitemallGoodsService goodsService;
    private final LitemallCjLinkageMapper linkageMapper;

    /** Per-pid ingest serialization: a concurrent first view waits and then reads locally. */
    private final ConcurrentHashMap<String, Object> inFlight = new ConcurrentHashMap<>();
    /** Per-pid last FAILED attempt (epoch ms) — outage back-off, config on-demand cooldown. */
    private final Map<String, Long> failedAttempts = new ConcurrentHashMap<>();

    public CjReviewIngestService(CJDropshippingConfig config,
                                 CJProductService cjProductService,
                                 LitemallCommentService commentService,
                                 LitemallGoodsService goodsService,
                                 LitemallCjLinkageMapper linkageMapper) {
        this.config = config;
        this.cjProductService = cjProductService;
        this.commentService = commentService;
        this.goodsService = goodsService;
        this.linkageMapper = linkageMapper;
    }

    /**
     * Ingest this good's CJ reviews if they were never landed. Returns quietly (no exception
     * ever escapes to the read path) whether it ingested, skipped, or failed.
     */
    public void ingestIfNeeded(LitemallGoods goods) {
        if (goods == null || goods.getId() == null
                || !"cj".equals(goods.getSource()) || !StringUtils.hasText(goods.getCjPid())
                || goods.getCjReviewsIngestedTime() != null) {
            return;
        }
        if (!config.isEnabled() || !StringUtils.hasText(config.getCjApiKey())) {
            return; // ACL off (prod today): serve local-only, marker stays NULL for later retry
        }
        String pid = goods.getCjPid();
        if (inCooldown(pid)) {
            return;
        }
        Object lock = inFlight.computeIfAbsent(pid, k -> new Object());
        synchronized (lock) {
            try {
                // Re-read inside the lock: a concurrent view may have finished the ingest.
                LitemallGoods fresh = goodsService.findById(goods.getId());
                if (fresh == null || fresh.getCjReviewsIngestedTime() != null) {
                    return;
                }
                ingest(fresh.getId(), pid);
            } catch (RuntimeException ex) {
                failedAttempts.put(pid, System.currentTimeMillis());
                logger.warn("CJ review ingest failed for goods {} (pid {}): {}",
                        goods.getId(), pid, ex.getMessage());
            } finally {
                inFlight.remove(pid);
            }
        }
    }

    private void ingest(Integer goodsId, String pid) {
        int inserted = 0;
        int duplicates = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            CJProductReviewData data = cjProductService.getProductComments(pid, page, PAGE_SIZE);
            if (data == null || data.getList() == null) {
                if (page == 1) {
                    // Nothing landed and CJ unreachable/erroring: no marker, retry later.
                    failedAttempts.put(pid, System.currentTimeMillis());
                    logger.warn("CJ review ingest got no page-1 data for goods {} (pid {}); will retry after cooldown",
                            goodsId, pid);
                    return;
                }
                break; // partial ingest: keep what landed, stamp below (cap is best-effort)
            }
            for (CJProductComment c : data.getList()) {
                try {
                    commentService.saveRetainingTimes(toComment(goodsId, c));
                    inserted++;
                } catch (DuplicateKeyException e) {
                    duplicates++; // already landed by an earlier/concurrent ingest
                }
            }
            if (data.getList().size() < PAGE_SIZE) {
                break; // last page at CJ
            }
        }
        linkageMapper.markCjReviewsIngested(goodsId);
        failedAttempts.remove(pid);
        logger.info("Ingested {} CJ reviews for goods {} (pid {}, {} duplicate(s) skipped); further reads are local",
                inserted, goodsId, pid, duplicates);
    }

    private LitemallComment toComment(Integer goodsId, CJProductComment c) {
        LitemallComment comment = new LitemallComment();
        comment.setValueId(goodsId);
        comment.setType((byte) 0);
        comment.setUserId(0); // no litemall_user; the author lives in author_name/author_avatar
        comment.setSource("cj");
        comment.setExternalId(String.valueOf(c.getCommentId()));
        comment.setAuthorName(truncate(c.getCommentUser(), MAX_AUTHOR_NAME));
        comment.setAuthorAvatar(truncate(c.getFlagIconUrl(), MAX_AUTHOR_AVATAR));
        comment.setStar(clampStar(c.getScore()));
        comment.setContent(truncate(c.getComment() != null ? c.getComment() : "", MAX_CONTENT));
        String[] pics = fitPicUrls(c.getCommentUrls());
        comment.setPicUrls(pics);
        comment.setHasPicture(pics.length > 0);
        comment.setAddTime(parseCommentDate(c.getCommentDate()));
        return comment;
    }

    private boolean inCooldown(String pid) {
        Long last = failedAttempts.get(pid);
        return last != null
                && System.currentTimeMillis() - last < config.getOnDemandCooldownSeconds() * 1000L;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** CJ scores arrive as strings; clamp to the 0-5 star range, defaulting to 5 on garbage. */
    private static short clampStar(String score) {
        try {
            return (short) Math.max(0, Math.min(5, Integer.parseInt(score.trim())));
        } catch (RuntimeException e) {
            return 5;
        }
    }

    /**
     * Keep the leading image URLs that fit the pic_urls varchar(1023) once JSON-serialized
     * ({@code JsonStringArrayTypeHandler}: 2 brackets + per-entry quotes/comma).
     */
    private static String[] fitPicUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return new String[0];
        }
        List<String> kept = new ArrayList<>(urls.size());
        int jsonLength = 2;
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            int cost = url.length() + 3; // quotes + separator
            if (jsonLength + cost > MAX_PIC_URLS_JSON) {
                break;
            }
            kept.add(url);
            jsonLength += cost;
        }
        return kept.toArray(new String[0]);
    }

    /**
     * CJ commentDate is nominally ISO-8601 but the format is not contractual; try offset and
     * local ISO forms, then the "yyyy-MM-dd HH:mm:ss" / date-only shapes, else fall back to
     * now() rather than dropping the review.
     */
    private static LocalDateTime parseCommentDate(String raw) {
        if (StringUtils.hasText(raw)) {
            String value = raw.trim();
            try {
                return OffsetDateTime.parse(value).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDateTime.parse(value, CJ_SPACE_FORMAT);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(value).atStartOfDay();
            } catch (DateTimeParseException ignored) {
            }
        }
        return LocalDateTime.now();
    }
}
