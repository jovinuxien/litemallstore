package org.linlinjava.litemall.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Validates every Flyway migration (V1-V14) runs cleanly against a real MySQL
 * instance spun up in Docker via Testcontainers.
 *
 * Requirements: Docker daemon must be running.
 * Does NOT need a local MySQL — fully self-contained.
 *
 * Run with: mvn test -pl litemall-db -Dtest=FlywayMigrationTest
 */
public class FlywayMigrationTest {

    // Total number of forward migration scripts (V1 through V14)
    private static final int EXPECTED_MIGRATIONS = 14;

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
        assertEquals(
                "Expected " + EXPECTED_MIGRATIONS + " migrations but " + result.migrationsExecuted + " ran",
                EXPECTED_MIGRATIONS,
                result.migrationsExecuted
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