package org.linlinjava.litemall.goods.application.search;

import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 26 Phase 3: the enrichment queue decides where the scarce, shared CJ daily points budget
 * goes. The CJ mirror stays broad (all 14 L1s) while the storefront is narrowed to the anchor, so
 * without an on-sale-first ordering a batch spends ~93% of its calls on products we no longer
 * sell — and on-sale goods missing a detail body never get one, which is exactly the
 * description==title population Phase 3 exists to drain.
 *
 * <p>Ordering cannot be asserted without a database, so this pins the SQL itself: the intent is
 * easy to delete by accident during an unrelated edit to this ORDER BY.
 */
public class EnrichmentPrioritisesOnSaleTest {

    private static final String MAPPER =
            "org/linlinjava/litemall/db/dao/LitemallCjProductMapper.xml";

    private String selectForEnrichmentSql() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(MAPPER)) {
            assertTrue(in != null, "mapper XML not on the classpath: " + MAPPER);
            new XMLMapperBuilder(in, configuration, MAPPER, configuration.getSqlFragments()).parse();
        }
        MappedStatement statement = configuration.getMappedStatement(
                "org.linlinjava.litemall.db.dao.LitemallCjProductMapper.selectForEnrichment");
        return statement.getBoundSql(Map.of("limit", 20)).getSql();
    }

    @Test
    public void onSaleGoodsAreEnrichedBeforeEverythingElse() throws Exception {
        String sql = selectForEnrichmentSql();
        String orderBy = sql.substring(sql.toLowerCase().indexOf("order by"));

        int onSale = orderBy.indexOf("is_on_sale");
        int neverEnriched = orderBy.indexOf("enriched_time is null");
        assertTrue(onSale >= 0, "on-sale priority missing from the enrichment queue: " + orderBy);
        assertTrue(onSale < neverEnriched,
                "on-sale must outrank never-enriched, else the broad mirror drains the CJ budget: " + orderBy);
    }

    /**
     * The goods join is a LEFT join, so an un-promoted mirror row has a NULL is_on_sale. Ranking
     * must not depend on MySQL's NULL ordering rules.
     */
    @Test
    public void theOnSaleTermIsNullSafe() throws Exception {
        String orderBy = selectForEnrichmentSql().toLowerCase();
        orderBy = orderBy.substring(orderBy.indexOf("order by"));
        int onSale = orderBy.indexOf("is_on_sale");
        String term = orderBy.substring(Math.max(0, onSale - 40), onSale + 20);
        assertTrue(term.contains("coalesce"), "on-sale ordering term must be NULL-safe: " + term);
    }

    /** The queue must still be a queue: never-enriched first, then stalest, within a priority. */
    @Test
    public void theExistingFairnessOrderingSurvives() throws Exception {
        String orderBy = selectForEnrichmentSql().toLowerCase();
        orderBy = orderBy.substring(orderBy.indexOf("order by"));
        for (String term : new String[]{"enriched_time is null", "views", "listed_num", "enriched_time asc"}) {
            assertTrue(orderBy.contains(term), "lost ordering term '" + term + "': " + orderBy);
        }
    }
}
