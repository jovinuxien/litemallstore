package org.linlinjava.litemall.goods.application.promo;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallPromoCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallPromoCandidate;
import org.linlinjava.litemall.goods.application.insight.InsightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wave-19 promo-candidate admin reads/decisions behind
 * {@code /srv/private/admin/insight/promo-candidates*}. List rows follow the CONTRACT:
 * goods context joined in, {@code categoryId} resolved to the L1 ROOT, suggestion/reasons
 * parsed to JSON values, margin fields null when cost is uncaptured. Decisions are CAS
 * flips from {@code proposed} (errno 653 on a lost race — the insight convention);
 * {@code consume} records the created coupon/combination id, called by the admin UI
 * AFTER a successful create on the existing promotion paths.
 */
@Service
public class PromoCandidateAdminService {

    private static final Logger log = LoggerFactory.getLogger(PromoCandidateAdminService.class);

    private final InsightMapper insightMapper;
    private final LitemallPromoCandidateMapper candidateMapper;
    private final CategoryRootResolver rootResolver;
    private final PromoCandidateNightlyTask nightlyTask;

    public PromoCandidateAdminService(InsightMapper insightMapper,
                                      LitemallPromoCandidateMapper candidateMapper,
                                      CategoryRootResolver rootResolver,
                                      PromoCandidateNightlyTask nightlyTask) {
        this.insightMapper = insightMapper;
        this.candidateMapper = candidateMapper;
        this.rootResolver = rootResolver;
        this.nightlyTask = nightlyTask;
    }

    /** Rows for a kind+day (day defaults to the latest scored day for that kind). */
    public Map<String, Object> list(String kind, LocalDate day, String status) {
        LocalDate effective = day != null ? day : candidateMapper.selectLatestDay(kind);
        Map<String, Object> data = new LinkedHashMap<>();
        if (effective == null) {
            data.put("day", null);
            data.put("list", List.of());
            return data;
        }
        List<Map<String, Object>> rows = insightMapper.selectPromoCandidateRows(kind, effective, status);
        for (Map<String, Object> row : rows) {
            Object leaf = row.get("categoryId");
            row.put("categoryId", rootResolver.rootOf(
                    leaf == null ? null : ((Number) leaf).intValue()));
            row.put("suggestion", PromoJson.parseObject((String) row.get("suggestion")));
            row.put("reasons", PromoJson.parseArray((String) row.get("reasons")));
        }
        data.put("day", effective.toString());
        data.put("list", rows);
        return data;
    }

    public InsightService.CandidateActionResult dismiss(String kind, int goodsId, LocalDate day) {
        return decide(kind, goodsId, day, LitemallPromoCandidate.STATUS_DISMISSED, null);
    }

    public InsightService.CandidateActionResult consume(String kind, int goodsId, LocalDate day,
                                                        Integer refId) {
        return decide(kind, goodsId, day, LitemallPromoCandidate.STATUS_CONSUMED, refId);
    }

    private InsightService.CandidateActionResult decide(String kind, int goodsId, LocalDate day,
                                                        String target, Integer refId) {
        LitemallPromoCandidate candidate = day != null
                ? candidateMapper.selectByKindGoodsAndDay(kind, goodsId, day)
                : candidateMapper.selectLatestByKindAndGoods(kind, goodsId);
        if (candidate == null || !LitemallPromoCandidate.STATUS_PROPOSED.equals(candidate.getStatus())) {
            return InsightService.CandidateActionResult.fail(InsightService.ERRNO_CANDIDATE,
                    "no proposed " + kind + " candidate for goods " + goodsId);
        }
        int flipped = candidateMapper.updateStatus(candidate.getId(),
                LitemallPromoCandidate.STATUS_PROPOSED, target, refId);
        if (flipped == 0) {
            return InsightService.CandidateActionResult.fail(InsightService.ERRNO_CANDIDATE,
                    kind + " candidate for goods " + goodsId + " was already decided");
        }
        log.info("promo candidate {} goods {} day {} -> {}{}", kind, goodsId,
                candidate.getDay(), target, refId != null ? " (ref " + refId + ")" : "");
        return InsightService.CandidateActionResult.ok(Map.of(
                "goodsId", goodsId, "kind", kind, "status", target));
    }

    /** Manual trigger for the nightly scoring — dev acceptance / admin re-run. */
    public Map<String, Object> run(LocalDate day) {
        return nightlyTask.run(day != null ? day : LocalDate.now());
    }
}
