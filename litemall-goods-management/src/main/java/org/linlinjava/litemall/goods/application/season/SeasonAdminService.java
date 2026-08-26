package org.linlinjava.litemall.goods.application.season;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.linlinjava.litemall.db.dao.LitemallSeasonCandidateMapper;
import org.linlinjava.litemall.db.dao.LitemallSeasonRuleMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallSeasonCandidate;
import org.linlinjava.litemall.db.domain.LitemallSeasonRule;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.domain.service.elastic.SeasonSignalResolver;
import org.springframework.stereotype.Service;

/**
 * Admin reads and the two decisions an admin can take: veto a product for a season, or undo that.
 *
 * <p>Publishing is automatic; this is the veto side of auto-with-veto. A dismissal is permanent by
 * design — {@code SeasonScoringService} re-writes it forward on every run, so it survives the day
 * boundary that the upsert guard alone does not cover.
 */
@Service
public class SeasonAdminService {

    /** Outcome of a decision, so the controller can map it to an errno without string-matching. */
    public enum Outcome { OK, NOT_FOUND, LOST_RACE }

    private final LitemallSeasonRuleMapper ruleMapper;
    private final LitemallSeasonCandidateMapper candidateMapper;
    private final LitemallGoodsService goodsService;
    private final SeasonSignalResolver signalResolver;

    public SeasonAdminService(LitemallSeasonRuleMapper ruleMapper,
                              LitemallSeasonCandidateMapper candidateMapper,
                              LitemallGoodsService goodsService,
                              SeasonSignalResolver signalResolver) {
        this.ruleMapper = ruleMapper;
        this.candidateMapper = candidateMapper;
        this.goodsService = goodsService;
        this.signalResolver = signalResolver;
    }

    /** Candidates for a season, defaulting to its most recent scoring day. */
    public Map<String, Object> candidates(String seasonKey, String status, LocalDate day) {
        LocalDate effectiveDay = day != null ? day : candidateMapper.selectLatestDay(seasonKey);
        List<Map<String, Object>> list = new ArrayList<>();
        if (effectiveDay != null) {
            List<LitemallSeasonCandidate> rows =
                    candidateMapper.selectBySeasonAndDay(seasonKey, effectiveDay, status);
            for (LitemallSeasonCandidate row : rows == null ? List.<LitemallSeasonCandidate>of() : rows) {
                list.add(toView(row));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seasonKey", seasonKey);
        out.put("day", effectiveDay == null ? null : effectiveDay.toString());
        out.put("list", list);
        return out;
    }

    private Map<String, Object> toView(LitemallSeasonCandidate row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("goodsId", row.getGoodsId());
        view.put("seasonKey", row.getSeasonKey());
        view.put("day", row.getDay() == null ? null : row.getDay().toString());
        view.put("tier", row.getTier());
        view.put("score", row.getScore());
        view.put("status", row.getStatus());
        view.put("reasons", row.getReasons());
        view.put("configVersionHash", row.getConfigVersionHash());
        view.put("configSnapshot", row.getConfigSnapshot());

        LitemallGoods goods = goodsService.findById(row.getGoodsId());
        view.put("name", goods == null ? null : goods.getName());
        view.put("picUrl", goods == null ? null : goods.getPicUrl());
        view.put("retailPrice", goods == null ? null : goods.getRetailPrice());
        view.put("cost", goods == null ? null : goods.getCost());
        // Margin stays null — never a fabricated 0 — when cost was not captured.
        view.put("marginPct", goods == null ? null
                : SeasonScoringService.marginPct(goods.getRetailPrice(), goods.getCost()));
        return view;
    }

    /** Permanent veto. CAS from whatever the row currently is, except an existing dismissal. */
    public Outcome dismiss(String seasonKey, int goodsId, LocalDate day) {
        return flip(seasonKey, goodsId, day, LitemallSeasonCandidate.STATUS_DISMISSED);
    }

    /**
     * Undo a veto: the row returns to {@code proposed}, and the next scoring run may publish it
     * again on its merits.
     */
    public Outcome restore(String seasonKey, int goodsId, LocalDate day) {
        return flip(seasonKey, goodsId, day, LitemallSeasonCandidate.STATUS_PROPOSED);
    }

    private Outcome flip(String seasonKey, int goodsId, LocalDate day, String toStatus) {
        LocalDate effectiveDay = day != null ? day : candidateMapper.selectLatestDay(seasonKey);
        if (effectiveDay == null) {
            return Outcome.NOT_FOUND;
        }
        int changed = 0;
        for (String from : List.of(LitemallSeasonCandidate.STATUS_PROPOSED,
                LitemallSeasonCandidate.STATUS_AUTO,
                LitemallSeasonCandidate.STATUS_DISMISSED)) {
            if (from.equals(toStatus)) {
                continue;
            }
            changed += candidateMapper.updateStatus(seasonKey, goodsId, effectiveDay, from, toStatus);
            if (changed > 0) {
                break;
            }
        }
        if (changed == 0) {
            // Either no such row, or it already held the target status — the caller's intent is
            // satisfied in the second case, so this reads as a lost race rather than an error.
            return Outcome.LOST_RACE;
        }
        signalResolver.invalidate();
        return Outcome.OK;
    }

    /** The season rules, for the admin list. */
    public List<LitemallSeasonRule> rules() {
        List<LitemallSeasonRule> rules = ruleMapper.selectAll(false);
        return rules == null ? List.of() : rules;
    }

    /** Patch one rule's tunable fields. Returns false when the key does not exist. */
    public boolean updateRule(LitemallSeasonRule patch) {
        if (patch == null || patch.getSeasonKey() == null || patch.getSeasonKey().isBlank()) {
            return false;
        }
        if (ruleMapper.selectByKey(patch.getSeasonKey()) == null) {
            return false;
        }
        boolean ok = ruleMapper.updateByKey(patch) > 0;
        if (ok) {
            signalResolver.invalidate();
        }
        return ok;
    }
}
