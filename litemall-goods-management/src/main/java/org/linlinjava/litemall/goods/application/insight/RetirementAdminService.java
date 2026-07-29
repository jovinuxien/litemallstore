package org.linlinjava.litemall.goods.application.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallRetireCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallRetireCandidate;
import org.linlinjava.litemall.goods.infrastructure.configuration.InventoryFlowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wave-14 retirement admin surface behind {@code /srv/private/admin/insight/retire-candidates}.
 * Lists joined candidate rows per status and applies admin decisions with real CAS semantics:
 * errno 653 whenever a goods has no live {@code proposed} row or the guarded transition finds
 * the row already decided (unlike the Wave-12 deal approve, which only warned — the Wave-14
 * contract wants the failure surfaced).
 *
 * <p>Approve is batch and PARTIAL-APPLY: every approvable goods is approved, then the response
 * is ok when all landed or errno 653 naming the failures (the successes stay approved — the
 * admin refetches the list either way). Dates cross the contract boundary as ISO strings, never
 * the module's LocalDate-as-array serialization.
 */
@Service
public class RetirementAdminService {

    private static final Logger log = LoggerFactory.getLogger(RetirementAdminService.class);

    static final int ERRNO_CANDIDATE = 653;
    private static final int LIST_LIMIT = 500;
    private static final Set<String> STATUSES = Set.of(
            LitemallRetireCandidate.STATUS_PROPOSED,
            LitemallRetireCandidate.STATUS_APPROVED,
            LitemallRetireCandidate.STATUS_DISMISSED,
            LitemallRetireCandidate.STATUS_EXECUTED);

    private final InsightMapper insightMapper;
    private final LitemallRetireCandidateMapper retireMapper;
    private final InventoryFlowProperties properties;
    private final ObjectMapper objectMapper;

    public RetirementAdminService(InsightMapper insightMapper,
                                  LitemallRetireCandidateMapper retireMapper,
                                  InventoryFlowProperties properties,
                                  ObjectMapper objectMapper) {
        this.insightMapper = insightMapper;
        this.retireMapper = retireMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Candidate rows joined with goods context, one row per goods (newest day wins). */
    public GovernanceResult list(String status) {
        String effective = status == null || status.isBlank()
                ? LitemallRetireCandidate.STATUS_PROPOSED : status;
        if (!STATUSES.contains(effective)) {
            return GovernanceResult.fail(402, "unknown status '" + status + "'");
        }
        List<Map<String, Object>> rows = insightMapper.selectRetireCandidateRows(effective, LIST_LIMIT);
        List<Map<String, Object>> list = new ArrayList<>();
        Set<Object> seenGoods = new HashSet<>();
        for (Map<String, Object> row : rows) {
            if (!seenGoods.add(row.get("goodsId"))) {
                continue; // ordered day desc — older duplicates of the same goods drop out
            }
            Map<String, Object> out = new LinkedHashMap<>(row);
            out.put("reasons", parseReasons((String) row.get("reasons")));
            out.put("day", isoDate(row.get("day")));
            out.put("executeOn", isoDate(row.get("executeOn")));
            list.add(out);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return GovernanceResult.ok(data);
    }

    /** Batch approve: CAS each goods' latest proposed row to approved with an execute_on date. */
    public GovernanceResult approve(List<Integer> goodsIds, LocalDate executeOn) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return GovernanceResult.fail(402, "goodsIds must not be empty");
        }
        LocalDate effective = executeOn != null ? executeOn : nextScheduledDay(LocalDate.now());
        List<Integer> approved = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Integer goodsId : goodsIds) {
            if (goodsId == null) {
                continue;
            }
            LitemallRetireCandidate latest = retireMapper.selectLatestProposedByGoods(goodsId);
            if (latest == null) {
                failed.add(failure(goodsId, "no proposed retire candidate"));
                continue;
            }
            if (retireMapper.updateStatus(latest.getId(), LitemallRetireCandidate.STATUS_PROPOSED,
                    LitemallRetireCandidate.STATUS_APPROVED, effective) > 0) {
                approved.add(goodsId);
            } else {
                failed.add(failure(goodsId, "already decided"));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("approved", approved);
        data.put("failed", failed);
        data.put("executeOn", effective.toString());
        if (!failed.isEmpty()) {
            return GovernanceResult.fail(ERRNO_CANDIDATE, failed.size() + " of " + goodsIds.size()
                    + " could not be approved (" + approved.size() + " approved for " + effective
                    + "): " + failed);
        }
        log.info("retirement: approved {} candidates for {}", approved.size(), effective);
        return GovernanceResult.ok(data);
    }

    public GovernanceResult dismiss(int goodsId) {
        LitemallRetireCandidate latest = retireMapper.selectLatestProposedByGoods(goodsId);
        if (latest == null) {
            return GovernanceResult.fail(ERRNO_CANDIDATE,
                    "no proposed retire candidate for goods " + goodsId);
        }
        if (retireMapper.updateStatus(latest.getId(), LitemallRetireCandidate.STATUS_PROPOSED,
                LitemallRetireCandidate.STATUS_DISMISSED, null) == 0) {
            return GovernanceResult.fail(ERRNO_CANDIDATE,
                    "retire candidate for goods " + goodsId + " already decided");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("goodsId", goodsId);
        data.put("status", LitemallRetireCandidate.STATUS_DISMISSED);
        return GovernanceResult.ok(data);
    }

    /** The next configured retirement weekday STRICTLY after today (today's 02:00 has passed). */
    LocalDate nextScheduledDay(LocalDate today) {
        return today.with(TemporalAdjusters.next(configuredDay()));
    }

    private DayOfWeek configuredDay() {
        String raw = properties.getRetireDefaultDay();
        if (raw != null && !raw.isBlank()) {
            String name = raw.trim().toUpperCase(Locale.ROOT);
            for (DayOfWeek day : DayOfWeek.values()) {
                if (day.name().equals(name) || day.name().startsWith(name)) {
                    return day;
                }
            }
            log.warn("unparseable litemall.inventoryflow.retire-default-day '{}' — using WEDNESDAY", raw);
        }
        return DayOfWeek.WEDNESDAY;
    }

    private static Map<String, Object> failure(Integer goodsId, String reason) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("goodsId", goodsId);
        f.put("reason", reason);
        return f;
    }

    private static String isoDate(Object day) {
        return day == null ? null : day.toString();
    }

    private List<String> parseReasons(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(json, String[].class));
        } catch (Exception e) {
            return List.of(json);
        }
    }
}
