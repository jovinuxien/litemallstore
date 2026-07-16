package org.linlinjava.litemall.order.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.db.dao.LitemallUserBrokerageRecordMapper;
import org.linlinjava.litemall.db.dao.LitemallUserExtractMapper;
import org.linlinjava.litemall.db.dao.LitemallUserMapper;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.domain.LitemallUserBrokerageRecord;
import org.linlinjava.litemall.db.domain.LitemallUserExtract;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Read side of the Wave-5 affiliate portal ({@code /srv/private/affiliate/**}).
 * Every method is scoped to ONE user id — the controller passes the
 * gateway-forwarded {@code X-User-Id} and nothing else, so there is no IDOR
 * surface to begin with. Writes (withdrawals) go through
 * {@link LitemallExtractServiceLayer}.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class LitemallAffiliateServiceLayer {

    private final LitemallUserMapper userMapper;
    private final LitemallUserBrokerageRecordMapper recordMapper;
    private final LitemallUserExtractMapper extractMapper;
    private final LitemallExtractServiceLayer extractServiceLayer;

    public LitemallAffiliateServiceLayer(LitemallUserMapper userMapper,
                                         LitemallUserBrokerageRecordMapper recordMapper,
                                         LitemallUserExtractMapper extractMapper,
                                         LitemallExtractServiceLayer extractServiceLayer) {
        this.userMapper = userMapper;
        this.recordMapper = recordMapper;
        this.extractMapper = extractMapper;
        this.extractServiceLayer = extractServiceLayer;
    }

    /** The caller as a live promoter, or null (not a promoter / demoted / deleted). */
    public LitemallUser findLivePromoter(Integer userId) {
        return userMapper.selectLivePromoter(userId);
    }

    /** Aggregate dashboard sums for one promoter (all straight off the ledger/user row). */
    public Dashboard dashboard(Integer userId) {
        BigDecimal available = userMapper.selectBrokeragePriceByUserId(userId);
        BigDecimal frozenSum = recordMapper.sumByUserAndStatus(userId,
                LitemallUserBrokerageRecord.STATUS_FROZEN, true);
        BigDecimal lifetimeEarned = recordMapper.sumByUserAndStatus(userId,
                LitemallUserBrokerageRecord.STATUS_VALID, true);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal thisMonth = recordMapper.sumEarnedSince(userId, monthStart);
        long spreadCount = userMapper.countBySpreadUid(userId);
        long referredOrders = recordMapper.countReferredOrders(userId);
        return new Dashboard(
                available == null ? BigDecimal.ZERO : available,
                frozenSum, lifetimeEarned, thisMonth, spreadCount, referredOrders);
    }

    public List<LitemallUserBrokerageRecord> records(Integer userId, int page, int limit) {
        return recordMapper.selectPageByUserId(userId, Math.max(0, (page - 1) * limit), limit);
    }

    public long countRecords(Integer userId) {
        return recordMapper.countByUserId(userId);
    }

    public List<LitemallUser> team(Integer userId, int page, int limit) {
        return userMapper.selectTeamBySpreadUid(userId, Math.max(0, (page - 1) * limit), limit);
    }

    public long countTeam(Integer userId) {
        return userMapper.countBySpreadUid(userId);
    }

    /** The caller's brokerage-sourced withdrawal requests, newest first. */
    public List<LitemallUserExtract> brokerageExtractHistory(Integer userId) {
        List<LitemallUserExtract> brokerageOnly = new ArrayList<>();
        for (LitemallUserExtract extract : extractMapper.selectByUserId(userId)) {
            // Brokerage-sourced = has a pm=0 ledger marker row (wallet extracts don't).
            if (recordMapper.selectExtractDebit(extract.getId()) != null) {
                brokerageOnly.add(extract);
            }
        }
        return brokerageOnly;
    }

    public record Dashboard(BigDecimal available, BigDecimal frozenSum, BigDecimal lifetimeEarned,
                            BigDecimal thisMonth, long spreadCount, long referredOrders) {
    }
}
