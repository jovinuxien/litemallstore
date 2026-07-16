package org.linlinjava.litemall.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.Assert.assertTrue;

/**
 * Validates every Flyway migration runs cleanly against a real MySQL
 * instance spun up in Docker via Testcontainers.
 *
 * Requirements: Docker daemon must be running.
 * Does NOT need a local MySQL — fully self-contained.
 *
 * Run with: mvn test -pl litemall-db -Dtest=FlywayMigrationTest
 */
// The schema spot-checks assume allMigrationsRunClean already migrated the
// container; JUnit's DEFAULT (name-hash) order runs them FIRST. Alphabetical
// order puts allMigrationsRunClean ahead of every spot-check.
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FlywayMigrationTest {

    // Hand-maintained floor: V35 (store pickup / write-off) is the latest known
    // migration; the exact-count assertion was replaced with a >= floor so this
    // test no longer goes stale every time a script lands.
    private static final int MIN_EXPECTED_MIGRATIONS = 35;

    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0.33")
                    .withDatabaseName("litemall_test")
                    .withUsername("litemall_test")
                    .withPassword("litemall_test");

    @BeforeClass
    public static void startContainer() {
        MYSQL.start();
    }

    @AfterClass
    public static void stopContainer() {
        MYSQL.stop();
    }

    // -------------------------------------------------------------------------
    // Core migration run
    // -------------------------------------------------------------------------

    @Test
    public void allMigrationsRunClean() {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                // Fresh container — no baseline needed
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();

        MigrateResult result = flyway.migrate();

        assertTrue("Flyway migration failed: " + result.warnings, result.success);
        assertTrue(
                "Expected at least " + MIN_EXPECTED_MIGRATIONS + " migrations but only "
                        + result.migrationsExecuted + " ran",
                result.migrationsExecuted >= MIN_EXPECTED_MIGRATIONS
        );
    }

    // -------------------------------------------------------------------------
    // Schema spot-checks — run after migration to verify key structures exist
    // -------------------------------------------------------------------------

    @Test
    public void newColumnsExistOnExistingTables() throws Exception {
        // Verifies V2 was applied: spot-check one column on each altered table
        String[][] checks = {
            {"litemall_user",         "now_money"},
            {"litemall_user",         "integral"},
            {"litemall_user",         "is_promoter"},
            {"litemall_address",      "latitude"},
            {"litemall_goods",        "vip_price"},
            {"litemall_goods",        "spec_type"},
            {"litemall_goods_product","cost"},
            {"litemall_cart",         "seckill_id"},
            {"litemall_order",        "deduction_price"},
            {"litemall_coupon",       "last_total"},
            {"litemall_coupon_user",  "use_type"},
            // V2: freight-template binding / billing units on goods
            {"litemall_goods",        "temp_id"},
            // V34: internationalized freight-template rows + default-template flag
            {"litemall_shipping_templates",        "is_default"},
            {"litemall_shipping_templates_region", "country_code"},
            {"litemall_shipping_templates_region", "province_name"},
            {"litemall_shipping_templates_free",   "country_code"},
            {"litemall_shipping_templates_free",   "province_name"},
            // V38: flash-deal price-swap lifecycle state on the V9 seckill table
            {"litemall_seckill",      "original_retail_price"},
            {"litemall_seckill",      "price_swapped"},
            // V35: in-store pickup / write-off columns on order
            {"litemall_order",        "delivery_type"},
            {"litemall_order",        "verify_code"},
        };

        try (var conn = MYSQL.createConnection("")) {
            for (String[] check : checks) {
                String table = check[0];
                String column = check[1];
                var rs = conn.getMetaData().getColumns(
                        MYSQL.getDatabaseName(), null, table, column);
                assertTrue(
                        "Missing column: " + table + "." + column,
                        rs.next()
                );
            }
        }
    }

    @Test
    public void newTablesExist() throws Exception {
        // Verifies V3-V14 tables were created
        String[] tables = {
            "litemall_user_bill",
            "litemall_user_recharge",
            "litemall_user_extract",
            "litemall_user_integral_record",
            "litemall_user_sign",
            "litemall_user_experience_record",
            "litemall_system_user_level",
            "litemall_user_level",
            "litemall_user_group",
            "litemall_user_tag",
            "litemall_user_token",
            "litemall_user_brokerage_record",
            "litemall_shipping_templates",
            "litemall_shipping_templates_free",
            "litemall_shipping_templates_region",
            "litemall_seckill_time",
            "litemall_seckill",
            "litemall_goods_related",
            "litemall_bargain",
            "litemall_bargain_user",
            "litemall_bargain_help",
            "litemall_order_status",
            "litemall_system_group",
            "litemall_system_group_data",
            "litemall_system_menu",
            "litemall_sms_template",
            "litemall_sms_record",
            "litemall_goods_rule",
            "litemall_goods_description",
            "litemall_goods_log",
            // V35: physical pickup stores
            "litemall_store",
        };

        try (var conn = MYSQL.createConnection("")) {
            for (String table : tables) {
                var rs = conn.getMetaData().getTables(
                        MYSQL.getDatabaseName(), null, table, new String[]{"TABLE"});
                assertTrue("Missing table: " + table, rs.next());
            }
        }
    }

    @Test
    public void baselineTablesStillExist() throws Exception {
        // Sanity-check that V1 baseline tables were not accidentally dropped
        String[] v1Tables = {
            "litemall_user", "litemall_goods", "litemall_order",
            "litemall_cart", "litemall_coupon", "litemall_coupon_user",
            "litemall_address", "litemall_category", "litemall_brand",
            "litemall_groupon", "litemall_groupon_rules",
            "litemall_recommendation", "litemall_recommendation_item",
            "litemall_user_behavior",
        };

        try (var conn = MYSQL.createConnection("")) {
            for (String table : v1Tables) {
                var rs = conn.getMetaData().getTables(
                        MYSQL.getDatabaseName(), null, table, new String[]{"TABLE"});
                assertTrue("Baseline table missing: " + table, rs.next());
            }
        }
    }
}