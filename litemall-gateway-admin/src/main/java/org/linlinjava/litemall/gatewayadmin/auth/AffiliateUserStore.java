package org.linlinjava.litemall.gatewayadmin.auth;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * Slim edge-local reader/writer for the affiliate-relevant columns of
 * {@code litemall_user} ({@code is_promoter}, {@code spread_*},
 * {@code brokerage_price}, ...).
 *
 * <p>Deliberately JDBC in this module rather than litemall-db: the shared
 * {@code LitemallUser} domain/mapper does not yet map these columns — that
 * hand-edit is owned by the order worktree this wave (Wave-5 block, litemall-db
 * result-map landmine), and this edge must not edit other modules. Once
 * order's litemall-db lands, callers can migrate to
 * {@code LitemallUserService}; the SQL here is confined to this one class.
 * Blocking JDBC — callers run it on a boundedElastic scheduler (same contract
 * as {@link AdminCredentialsService}).
 */
@Service
public class AffiliateUserStore {

    /** Columns needed for affiliate login/refresh and the admin promoters panel. */
    private static final String COLS =
            "id, username, nickname, mobile, avatar, password, is_promoter, spread_uid, "
            + "spread_count, pay_count, brokerage_price, add_time, deleted";

    private static final RowMapper<AffiliateUser> MAPPER = (rs, i) -> {
        AffiliateUser u = new AffiliateUser();
        u.id = rs.getInt("id");
        u.username = rs.getString("username");
        u.nickname = rs.getString("nickname");
        u.mobile = rs.getString("mobile");
        u.avatar = rs.getString("avatar");
        u.password = rs.getString("password");
        u.promoter = rs.getBoolean("is_promoter");
        u.spreadUid = rs.getInt("spread_uid");
        u.spreadCount = rs.getInt("spread_count");
        u.payCount = rs.getInt("pay_count");
        u.brokeragePrice = rs.getBigDecimal("brokerage_price");
        java.sql.Timestamp t = rs.getTimestamp("add_time");
        u.addTime = t == null ? null : t.toLocalDateTime();
        u.deleted = rs.getBoolean("deleted");
        return u;
    };

    private final JdbcTemplate jdbc;

    public AffiliateUserStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Live (non-deleted) user by exact username, or null. Username is UNIQUE. */
    public AffiliateUser findLiveByUsername(String username) {
        try {
            return jdbc.queryForObject(
                    "SELECT " + COLS + " FROM litemall_user WHERE username = ? AND deleted = 0",
                    MAPPER, username);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** Live (non-deleted) user by id, or null. */
    public AffiliateUser findLiveById(Integer id) {
        try {
            return jdbc.queryForObject(
                    "SELECT " + COLS + " FROM litemall_user WHERE id = ? AND deleted = 0",
                    MAPPER, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * Admin promoters panel: paged live-user search by username/nickname/mobile
     * substring (blank = all), optionally promoters only, newest first.
     */
    public List<AffiliateUser> search(String q, boolean promotersOnly, int page, int limit) {
        String where = whereFor(q, promotersOnly);
        Object[] args = argsFor(q);
        int offset = Math.max(0, (page - 1) * limit);
        Object[] all = new Object[args.length + 2];
        System.arraycopy(args, 0, all, 0, args.length);
        all[args.length] = limit;
        all[args.length + 1] = offset;
        return jdbc.query(
                "SELECT " + COLS + " FROM litemall_user " + where
                        + " ORDER BY id DESC LIMIT ? OFFSET ?",
                MAPPER, all);
    }

    public long count(String q, boolean promotersOnly) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM litemall_user " + whereFor(q, promotersOnly),
                Long.class, argsFor(q));
        return n == null ? 0 : n;
    }

    private static String whereFor(String q, boolean promotersOnly) {
        StringBuilder where = new StringBuilder("WHERE deleted = 0");
        if (q != null && !q.isBlank()) {
            where.append(" AND (username LIKE ? OR nickname LIKE ? OR mobile LIKE ?)");
        }
        if (promotersOnly) {
            where.append(" AND is_promoter = 1");
        }
        return where.toString();
    }

    private static Object[] argsFor(String q) {
        if (q == null || q.isBlank()) {
            return new Object[0];
        }
        String like = "%" + q.trim() + "%";
        return new Object[] { like, like, like };
    }

    /**
     * Per-affiliate brokerage ledger (V7 {@code litemall_user_brokerage_record})
     * for the admin Promoters panel — read-only, newest first. The write side
     * of this table is exclusively order's BrokerageService.
     */
    public List<java.util.Map<String, Object>> ledger(Integer userId, int page, int limit) {
        int offset = Math.max(0, (page - 1) * limit);
        return jdbc.queryForList(
                "SELECT id, link_id, link_type, pm, title, price, balance, mark, status, "
                        + "freeze_time, unfreeze_time, add_time "
                        + "FROM litemall_user_brokerage_record "
                        + "WHERE user_id = ? AND deleted = 0 ORDER BY add_time DESC, id DESC "
                        + "LIMIT ? OFFSET ?",
                userId, limit, offset);
    }

    public long ledgerCount(Integer userId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM litemall_user_brokerage_record WHERE user_id = ? AND deleted = 0",
                Long.class, userId);
        return n == null ? 0 : n;
    }

    /** Guarded promoter toggle; 0 rows = unknown/deleted user. */
    public int setPromoter(Integer userId, boolean promoter) {
        return jdbc.update(
                "UPDATE litemall_user SET is_promoter = ?, update_time = NOW() "
                        + "WHERE id = ? AND deleted = 0",
                promoter ? 1 : 0, userId);
    }

    /** Projection of litemall_user for the affiliate realm. */
    public static class AffiliateUser {
        private Integer id;
        private String username;
        private String nickname;
        private String mobile;
        private String avatar;
        private String password;
        private boolean promoter;
        private Integer spreadUid;
        private Integer spreadCount;
        private Integer payCount;
        private BigDecimal brokeragePrice;
        private LocalDateTime addTime;
        private boolean deleted;

        public Integer getId() { return id; }
        public String getUsername() { return username; }
        public String getNickname() { return nickname; }
        public String getMobile() { return mobile; }
        public String getAvatar() { return avatar; }
        public String getPassword() { return password; }
        public boolean isPromoter() { return promoter; }
        public Integer getSpreadUid() { return spreadUid; }
        public Integer getSpreadCount() { return spreadCount; }
        public Integer getPayCount() { return payCount; }
        public BigDecimal getBrokeragePrice() { return brokeragePrice; }
        public LocalDateTime getAddTime() { return addTime; }
        public boolean isDeleted() { return deleted; }
    }
}
