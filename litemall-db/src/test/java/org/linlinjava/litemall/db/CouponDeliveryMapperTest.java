package org.linlinjava.litemall.db;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallCouponDeliveryMapper;
import org.linlinjava.litemall.db.domain.LitemallCouponDelivery;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave-22 semantics of {@code LitemallCouponDeliveryMapper} against real MySQL
 * (Testcontainers, migrated by Flyway — SKIPPED, not failed, without Docker):
 *
 * <ul>
 *   <li>the paid-status set: {@code order_status >= 201} (the house
 *       paid-or-later convention) — 101/102/103/104 rows never count;</li>
 *   <li>each segment criterion alone and combined (recency over
 *       {@code coalesce(pay_time, add_time)}, lifetime frequency, lifetime
 *       monetary), plus the cap {@code limit};</li>
 *   <li>performance inputs: granted / used / ordersCount / revenue over
 *       {@code litemall_coupon_user} + the used rows' orders;</li>
 *   <li>delivery-row insert + paged history + count.</li>
 * </ul>
 *
 * JUnit 5 on purpose — the module's legacy JUnit 4 tests are not discovered
 * (no vintage engine); check the "Tests run:" count.
 */
public class CouponDeliveryMapperTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 8, 12, 0);

    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0.33")
                    .withDatabaseName("litemall_test")
                    .withUsername("litemall_test")
                    .withPassword("litemall_test");

    private static SqlSessionFactory factory;

    @BeforeAll
    public static void setUp() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker unavailable — skipping coupon-delivery mapper validation");
        MYSQL.start();

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load()
                .migrate();

        UnpooledDataSource ds = new UnpooledDataSource(
                MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(),
                MYSQL.getUsername(), MYSQL.getPassword());
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), ds));
        configuration.setMapUnderscoreToCamelCase(true);
        // Finds the sibling XML at org/linlinjava/litemall/db/dao/.
        configuration.addMapper(LitemallCouponDeliveryMapper.class);
        factory = new SqlSessionFactoryBuilder().build(configuration);

        seed();
    }

    @AfterAll
    public static void tearDown() {
        if (MYSQL.isRunning()) {
            MYSQL.stop();
        }
    }

    /**
     * Fixture (users / paid history, all lifetime):
     * <ul>
     *   <li>u1: one order per unpaid/cancelled status 101, 102, 103, 104 — must NEVER match;</li>
     *   <li>u2: 1 paid order (201), $40, paid 5 days ago;</li>
     *   <li>u3: 3 paid orders (201, 301, 401), $150 total, last paid 60 days ago;</li>
     *   <li>u4: 2 paid orders (202, 203 — refund states still >= 201, the house set),
     *       $80 total, last 2 days ago, pay_time NULL on both (recency exists only
     *       via the add_time fallback);</li>
     *   <li>u5: 1 paid order but soft-deleted — never matches.</li>
     * </ul>
     * Coupon 900 holdings: u2 usable(0), u3 used(1)->order 9031 ($50 actual),
     * u4 used(1)->order 9041 ($30 actual), u5 used(1) but coupon row deleted,
     * plus a used(1) row pointing at a DELETED order (drops from orders/revenue
     * but still counts as used).
     */
    private static void seed() throws Exception {
        try (Connection c = MYSQL.createConnection("")) {
            // u1: never-paid statuses (104 admin-cancel must be excluded by >= 201 too)
            order(c, 9011, 1, 101, "10.00", days(-1), null, false);
            order(c, 9012, 1, 102, "10.00", days(-1), null, false);
            order(c, 9013, 1, 103, "10.00", days(-1), null, false);
            order(c, 9014, 1, 104, "10.00", days(-1), null, false);
            // u2: one recent paid order
            order(c, 9021, 2, 201, "40.00", days(-5), days(-5), false);
            // u3: frequent + high monetary, but lapsed (last paid 60 days ago)
            order(c, 9031, 3, 201, "50.00", days(-90), days(-90), false);
            order(c, 9032, 3, 301, "50.00", days(-75), days(-75), false);
            order(c, 9033, 3, 401, "50.00", days(-60), days(-60), false);
            // u4: refund-state orders (house set counts them); pay_time NULL on
            // BOTH so u4's recency exists only via the add_time fallback
            order(c, 9041, 4, 202, "30.00", days(-2), null, false);
            order(c, 9042, 4, 203, "50.00", days(-30), null, false);
            // u5: paid but soft-deleted order
            order(c, 9051, 5, 201, "99.00", days(-1), days(-1), true);

            // coupon 900 holdings
            couponUser(c, 900, 2, 0, null, false);
            couponUser(c, 900, 3, 1, 9031, false);
            couponUser(c, 900, 4, 1, 9041, false);
            couponUser(c, 900, 5, 1, 9051, false); // deleted coupon_user row
            markCouponUserDeleted(c, 900, 5);
            couponUser(c, 900, 6, 1, 9051, false); // used, but its order is deleted
        }
    }

    private static Timestamp days(int delta) {
        return Timestamp.valueOf(NOW.plusDays(delta));
    }

    private static void order(Connection c, int id, int userId, int status, String actual,
                              Timestamp addTime, Timestamp payTime, boolean deleted) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "insert into litemall_order (id, user_id, order_sn, order_status, consignee, mobile,"
                        + " address, goods_price, freight_price, coupon_price, integral_price,"
                        + " groupon_price, order_price, actual_price, pay_time, add_time, deleted)"
                        + " values (?,?,?,?,'t','1','a',0,0,0,0,0,?,?,?,?,?)")) {
            ps.setInt(1, id);
            ps.setInt(2, userId);
            ps.setString(3, "SN" + id);
            ps.setInt(4, status);
            ps.setBigDecimal(5, new BigDecimal(actual));
            ps.setBigDecimal(6, new BigDecimal(actual));
            ps.setTimestamp(7, payTime);
            ps.setTimestamp(8, addTime);
            ps.setBoolean(9, deleted);
            ps.executeUpdate();
        }
    }

    private static void couponUser(Connection c, int couponId, int userId, int status,
                                   Integer orderId, boolean deleted) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "insert into litemall_coupon_user (user_id, coupon_id, status, order_id, add_time, deleted)"
                        + " values (?,?,?,?,now(),?)")) {
            ps.setInt(1, userId);
            ps.setInt(2, couponId);
            ps.setInt(3, status);
            if (orderId != null) {
                ps.setInt(4, orderId);
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            ps.setBoolean(5, deleted);
            ps.executeUpdate();
        }
    }

    private static void markCouponUserDeleted(Connection c, int couponId, int userId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "update litemall_coupon_user set deleted = 1 where coupon_id = ? and user_id = ?")) {
            ps.setInt(1, couponId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private List<Integer> audience(LocalDateTime paidSince, Integer minFrequency,
                                   BigDecimal minMonetary, int limit) {
        try (SqlSession session = factory.openSession()) {
            return session.getMapper(LitemallCouponDeliveryMapper.class)
                    .selectPaidAudience(paidSince, minFrequency, minMonetary, limit);
        }
    }

    // ---- paid-status set ------------------------------------------------

    @Test
    public void paidStatusSet_excludesUnpaidCancelledAndDeleted() {
        // No criteria: every payer, capped. u1 (101-104 only) and u5 (deleted) never appear.
        List<Integer> all = audience(null, null, null, 100);
        assertEquals(List.of(2, 3, 4), all);
    }

    // ---- each criterion -------------------------------------------------

    @Test
    public void recencyCriterion_usesPayTimeWithAddTimeFallback() {
        // Last paid within 7 days: u2 (pay_time -5d), u4 (NO pay_time anywhere —
        // qualifies ONLY through the add_time fallback, -2d). u3's last is 60d.
        assertEquals(List.of(2, 4), audience(NOW.minusDays(7), null, null, 100));
        // Within 45 days: unchanged membership; u3 still out (last 60d).
        assertEquals(List.of(2, 4), audience(NOW.minusDays(45), null, null, 100));
        // Within 90 days everyone with paid history qualifies.
        assertEquals(List.of(2, 3, 4), audience(NOW.minusDays(90), null, null, 100));
    }

    @Test
    public void frequencyCriterion_countsLifetimePaidOrders() {
        assertEquals(List.of(3, 4), audience(null, 2, null, 100));
        assertEquals(List.of(3), audience(null, 3, null, 100));
    }

    @Test
    public void monetaryCriterion_sumsLifetimePaidActualPrice() {
        // u2 $40, u3 $150, u4 $80
        assertEquals(List.of(3, 4), audience(null, null, new BigDecimal("50.00"), 100));
        assertEquals(List.of(3), audience(null, null, new BigDecimal("100.00"), 100));
    }

    // ---- combinations + cap --------------------------------------------

    @Test
    public void combinedCriteria_intersect() {
        // Paid within 30 days AND >= $50 lifetime: only u4 ($80, last 2d).
        assertEquals(List.of(4), audience(NOW.minusDays(30), null, new BigDecimal("50.00"), 100));
        // All three: within 90d, >= 2 orders, >= $100 — only u3.
        assertEquals(List.of(3), audience(NOW.minusDays(90), 2, new BigDecimal("100.00"), 100));
    }

    @Test
    public void capLimitsTheSweep() {
        assertEquals(2, audience(null, null, null, 2).size());
    }

    // ---- performance inputs --------------------------------------------

    @Test
    public void performanceRow_grantedUsedOrdersRevenue() {
        try (SqlSession session = factory.openSession()) {
            Map<String, Object> row = session.getMapper(LitemallCouponDeliveryMapper.class)
                    .selectPerformance(900);
            // granted: u2 usable + u3/u4/u6 used (u5's row deleted) = 4
            assertEquals(4L, ((Number) row.get("granted")).longValue());
            // used: u3, u4, u6 = 3
            assertEquals(3L, ((Number) row.get("used")).longValue());
            // orders: u6's order is deleted -> only 9031 + 9041
            assertEquals(2L, ((Number) row.get("ordersCount")).longValue());
            assertEquals(0, new BigDecimal("80.00")
                    .compareTo((BigDecimal) row.get("revenue")));
        }
    }

    @Test
    public void performanceRow_alwaysPresentWhenNothingGranted() {
        try (SqlSession session = factory.openSession()) {
            Map<String, Object> row = session.getMapper(LitemallCouponDeliveryMapper.class)
                    .selectPerformance(901);
            assertNotNull(row);
            assertEquals(0L, ((Number) row.get("granted")).longValue());
            assertEquals(0L, ((Number) row.get("used")).longValue());
            assertEquals(0L, ((Number) row.get("ordersCount")).longValue());
            assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) row.get("revenue")));
        }
    }

    // ---- delivery history ledger ---------------------------------------

    @Test
    public void insertSelectAndCountDeliveries() {
        try (SqlSession session = factory.openSession(true)) {
            LitemallCouponDeliveryMapper mapper =
                    session.getMapper(LitemallCouponDeliveryMapper.class);

            LitemallCouponDelivery first = new LitemallCouponDelivery();
            first.setCouponId(950);
            first.setSegmentJson("{\"recencyDays\":30}");
            first.setMatched(12);
            first.setGranted(10);
            first.setSkipped(2);
            mapper.insertDelivery(first);
            assertNotNull(first.getId(), "generated key expected");

            LitemallCouponDelivery second = new LitemallCouponDelivery();
            second.setCouponId(951);
            second.setSegmentJson("{\"minMonetary\":50}");
            second.setMatched(3);
            second.setGranted(3);
            second.setSkipped(0);
            mapper.insertDelivery(second);

            assertEquals(1L, mapper.countByCoupon(950));
            assertEquals(2L, mapper.countByCoupon(null));

            List<LitemallCouponDelivery> rows = mapper.selectByCoupon(950, 0, 20);
            assertEquals(1, rows.size());
            LitemallCouponDelivery row = rows.get(0);
            assertEquals(950, row.getCouponId());
            assertEquals("{\"recencyDays\":30}", row.getSegmentJson());
            assertEquals(12, row.getMatched());
            assertEquals(10, row.getGranted());
            assertEquals(2, row.getSkipped());
            assertNotNull(row.getAddTime());

            // paging: newest first, offset walks the ledger
            List<Integer> ids = mapper.selectByCoupon(null, 0, 1).stream()
                    .map(LitemallCouponDelivery::getId).toList();
            assertEquals(List.of(second.getId()), ids);
            assertTrue(mapper.selectByCoupon(null, 2, 1).isEmpty());
        }
    }
}
