package org.linlinjava.litemall.db;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallBrandMapper;
import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.dao.LitemallCjProductMapper;
import org.linlinjava.litemall.db.dao.LitemallTopicMapper;
import org.linlinjava.litemall.db.domain.LitemallTopic;
import org.linlinjava.litemall.db.service.LitemallTopicService;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code LitemallTopicService#attachGoodsCounts} against real MySQL (Testcontainers, migrated by
 * Flyway — SKIPPED, not failed, without Docker). A real database is the point: the two things
 * inspection cannot confirm are that {@code selectOnSaleGoodsIds} is valid SQL and that the
 * {@code goods} JSON column decodes through {@code JsonIntegerArrayTypeHandler} on a *selective*
 * select, which is how the count is read.
 *
 * <p>The invariant under test: a topic's {@code goodsCount} equals the number of products
 * {@code /srv/topic/detail} would render for it, because both apply the same on-sale +
 * not-deleted filter.
 *
 * <p>JUnit 5 on purpose — the module's legacy JUnit 4 tests are not discovered (no vintage
 * engine); check the "Tests run:" count.
 */
public class TopicGoodsCountTest {

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
                "Docker unavailable — skipping topic goods-count validation");
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
        // Finds the sibling XMLs at org/linlinjava/litemall/db/dao/.
        configuration.addMapper(LitemallTopicMapper.class);
        configuration.addMapper(LitemallCjLinkageMapper.class);
        // LitemallCjLinkageMapper.xml <include>s Base_Column_List from these two. MyBatis parses
        // statements lazily, so a missing fragment surfaces as a failure on first call, not at
        // build time.
        configuration.addMapper(LitemallBrandMapper.class);
        configuration.addMapper(LitemallCjProductMapper.class);
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
     * Goods: 5001/5002 live; 5003 off-sale; 5004 soft-deleted; 5005 never inserted.
     * Topics: 7001 mixed (2 live of 5 listed); 7002 empty array; 7003 the column default '';
     * 7004 shares 5001 with 7001; 7005 lists 5001 twice; 7006 all-dead; 7007 soft-deleted topic.
     */
    private static void seed() throws Exception {
        try (Connection c = MYSQL.createConnection("")) {
            goods(c, 5001, "live one", true, false);
            goods(c, 5002, "live two", true, false);
            goods(c, 5003, "off sale", false, false);
            goods(c, 5004, "deleted", true, true);

            topic(c, 7001, "mixed", "[5001,5002,5003,5004,5005]", false);
            topic(c, 7002, "empty array", "[]", false);
            topic(c, 7003, "column default", "", false);
            topic(c, 7004, "shares 5001", "[5001]", false);
            topic(c, 7005, "duplicate id", "[5001,5001]", false);
            topic(c, 7006, "all dead", "[5003,5004]", false);
            topic(c, 7007, "soft-deleted topic", "[5001]", true);
        }
    }

    private static void goods(Connection c, int id, String name, boolean onSale, boolean deleted)
            throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "insert into litemall_goods (id, name, is_on_sale, deleted) values (?, ?, ?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setBoolean(3, onSale);
            ps.setBoolean(4, deleted);
            ps.executeUpdate();
        }
    }

    private static void topic(Connection c, int id, String title, String goodsJson, boolean deleted)
            throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "insert into litemall_topic (id, title, goods, deleted) values (?, ?, ?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, title);
            ps.setString(3, goodsJson);
            ps.setBoolean(4, deleted);
            ps.executeUpdate();
        }
    }

    /** The service with its two @Resource mappers bound to the test session. */
    private static LitemallTopicService serviceOn(SqlSession session) throws Exception {
        LitemallTopicService service = new LitemallTopicService();
        set(service, "topicMapper", session.getMapper(LitemallTopicMapper.class));
        set(service, "linkageMapper", session.getMapper(LitemallCjLinkageMapper.class));
        return service;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = LitemallTopicService.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static LitemallTopic row(int id) {
        LitemallTopic topic = new LitemallTopic();
        topic.setId(id);
        return topic;
    }

    private static List<LitemallTopic> rows(int... ids) {
        List<LitemallTopic> topics = new ArrayList<>();
        for (int id : ids) {
            topics.add(row(id));
        }
        return topics;
    }

    @Test
    public void countsOnlyLiveGoods() throws Exception {
        try (SqlSession session = factory.openSession()) {
            List<LitemallTopic> topics = rows(7001);
            serviceOn(session).attachGoodsCounts(topics);
            // 5001 + 5002 live; 5003 off-sale, 5004 deleted, 5005 absent all drop.
            assertEquals(2, topics.get(0).getGoodsCount());
        }
    }

    @Test
    public void emptyAndDefaultGoodsColumnsCountZeroNotNull() throws Exception {
        try (SqlSession session = factory.openSession()) {
            List<LitemallTopic> topics = rows(7002, 7003);
            serviceOn(session).attachGoodsCounts(topics);
            // Zero is a measurement ("nothing to show"); null would mean "not measured".
            assertEquals(0, topics.get(0).getGoodsCount());
            assertEquals(0, topics.get(1).getGoodsCount());
        }
    }

    @Test
    public void topicWhoseGoodsAreAllOffSaleCountsZero() throws Exception {
        try (SqlSession session = factory.openSession()) {
            List<LitemallTopic> topics = rows(7006);
            serviceOn(session).attachGoodsCounts(topics);
            // The state the Wave-26 narrowing left every seed topic in.
            assertEquals(0, topics.get(0).getGoodsCount());
        }
    }

    @Test
    public void oneGoodsIdSharedByTwoTopicsCountsForBoth() throws Exception {
        try (SqlSession session = factory.openSession()) {
            List<LitemallTopic> topics = rows(7001, 7004);
            serviceOn(session).attachGoodsCounts(topics);
            // The live ids are resolved once for the whole page; sharing must not consume them.
            assertEquals(2, topics.get(0).getGoodsCount());
            assertEquals(1, topics.get(1).getGoodsCount());
        }
    }

    @Test
    public void duplicateIdCountsTwiceMatchingWhatDetailRenders() throws Exception {
        try (SqlSession session = factory.openSession()) {
            List<LitemallTopic> topics = rows(7005);
            serviceOn(session).attachGoodsCounts(topics);
            // detail() renders one tile per entry, so the count follows it rather than dedup.
            assertEquals(2, topics.get(0).getGoodsCount());
        }
    }

    @Test
    public void softDeletedTopicCountsZeroRatherThanLeakingItsGoods() throws Exception {
        try (SqlSession session = factory.openSession()) {
            List<LitemallTopic> topics = rows(7007);
            serviceOn(session).attachGoodsCounts(topics);
            assertEquals(0, topics.get(0).getGoodsCount());
        }
    }

    @Test
    public void unknownTopicIdCountsZero() throws Exception {
        try (SqlSession session = factory.openSession()) {
            List<LitemallTopic> topics = rows(999999);
            serviceOn(session).attachGoodsCounts(topics);
            assertEquals(0, topics.get(0).getGoodsCount());
        }
    }

    @Test
    public void emptyAndNullInputsAreNoOps() throws Exception {
        try (SqlSession session = factory.openSession()) {
            LitemallTopicService service = serviceOn(session);
            service.attachGoodsCounts(null);
            service.attachGoodsCounts(new ArrayList<>());

            // A row with no id cannot be counted; it must stay unmeasured, not become 0.
            List<LitemallTopic> idless = new ArrayList<>();
            idless.add(new LitemallTopic());
            service.attachGoodsCounts(idless);
            assertNull(idless.get(0).getGoodsCount());
        }
    }

    @Test
    public void attachesInPlaceSoThePageHelperTotalSurvives() throws Exception {
        try (SqlSession session = factory.openSession()) {
            List<LitemallTopic> topics = rows(7001);
            List<LitemallTopic> same = topics;
            serviceOn(session).attachGoodsCounts(topics);
            // ResponseUtil.okList reads total/pages off the Page instance it is handed; rebuilding
            // the list to bolt a count on would silently collapse total to the page size.
            assertSame(same, topics);
            assertEquals(2, topics.get(0).getGoodsCount());
        }
    }
}
