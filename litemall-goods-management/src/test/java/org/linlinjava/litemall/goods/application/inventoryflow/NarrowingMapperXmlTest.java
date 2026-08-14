package org.linlinjava.litemall.goods.application.inventoryflow;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallRetireCandidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The narrowing statements live in hand-maintained mapper XML, which no unit test loads — a typo
 * there is invisible until the service boots (and, in the shared-litemall-db layout, it takes
 * EVERY dependent service down with it). This parses the two XMLs the way MyBatis does at
 * startup and renders the SQL, so the failure happens here instead.
 */
public class NarrowingMapperXmlTest {

    private static final String INSIGHT =
            "org/linlinjava/litemall/db/dao/InsightMapper.xml";
    private static final String RETIRE =
            "org/linlinjava/litemall/db/dao/LitemallRetireCandidateMapper.xml";

    private Configuration parse(String... resources) throws Exception {
        Configuration configuration = new Configuration();
        for (String resource : resources) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertTrue(in != null, "mapper XML not on the classpath: " + resource);
                new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }

    @Test
    public void narrowingStatementsParse() throws Exception {
        Configuration configuration = parse(INSIGHT, RETIRE);
        for (String id : List.of(
                "org.linlinjava.litemall.db.dao.InsightMapper.selectOnSaleCjPage",
                "org.linlinjava.litemall.db.dao.InsightMapper.selectOnSaleCjCountsByCategory",
                "org.linlinjava.litemall.db.dao.InsightMapper.selectNarrowedOffSale",
                "org.linlinjava.litemall.db.dao.LitemallRetireCandidateMapper.insertApprovedBatch")) {
            assertTrue(configuration.hasStatement(id), "missing statement " + id);
        }
    }

    @Test
    public void keysetPageBindsBothParameters() throws Exception {
        Configuration configuration = parse(INSIGHT);
        MappedStatement statement = configuration.getMappedStatement(
                "org.linlinjava.litemall.db.dao.InsightMapper.selectOnSaleCjPage");
        BoundSql sql = statement.getBoundSql(Map.of("afterId", 100, "limit", 500));
        assertTrue(sql.getSql().contains("is_on_sale = 1"), sql.getSql());
        assertTrue(sql.getSql().contains("source = 'cj'"), sql.getSql());
        assertEquals(2, sql.getParameterMappings().size());
    }

    /**
     * The ODKU assignment order is load-bearing: MySQL reads a column's NEW value after its own
     * assignment, so {@code status} must come LAST or every guard above it would test the value
     * it just wrote and the statement would overwrite decided rows.
     */
    @Test
    public void batchInsertAssignsStatusLastSoGuardsSeeTheOldStatus() throws Exception {
        Configuration configuration = parse(RETIRE);
        MappedStatement statement = configuration.getMappedStatement(
                "org.linlinjava.litemall.db.dao.LitemallRetireCandidateMapper.insertApprovedBatch");
        LitemallRetireCandidate row = new LitemallRetireCandidate();
        row.setGoodsId(7);
        row.setDay(LocalDate.of(2026, 8, 14));
        row.setExecuteOn(LocalDate.of(2026, 8, 14));
        row.setReasons("[\"non-anchor category\"]");
        String sql = statement.getBoundSql(Map.of("rows", List.of(row))).getSql();

        String tail = sql.substring(sql.indexOf("on duplicate key update"));
        assertTrue(tail.indexOf("execute_on") < tail.indexOf("status      = if"), tail);
        assertTrue(tail.indexOf("reasons") < tail.indexOf("status      = if"), tail);
        assertTrue(tail.contains("if(status = 'proposed', 'approved', status)"), tail);
        // Inserted rows land approved outright; only duplicates go through the guard.
        assertTrue(sql.contains("'approved'"), sql);
    }
}
