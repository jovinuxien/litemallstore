package org.linlinjava.litemall.db;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.linlinjava.litemall.db.dao.LitemallSystemMapper;
import org.linlinjava.litemall.db.domain.LitemallSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * Asserts the generated mappers return the affected-row counts callers rely on
 * (notably that an update against a deleted row returns 0, not 1).
 *
 * <p>Wave 7: this class did not compile. It referenced {@code LitemallSystem} and
 * {@code LitemallSystemMapper} with no imports — they live in {@code ..db.domain}
 * and {@code ..db.dao}, not {@code ..db}. That stayed invisible because the root
 * pom sets {@code maven.test.skip=true}, which skips test COMPILATION as well as
 * execution, so nothing ever tried to build it. Task D lifts that flag, so the
 * imports are fixed here rather than the class being deleted — the behaviour it
 * pins is real.
 *
 * <p>It is a {@code @SpringBootTest} that inserts and deletes real rows, so it
 * self-skips unless MYSQL_PASSWORD is set: CI stays green with no database, and
 * it does real work when one is available.
 */
@WebAppConfiguration
@RunWith(SpringRunner.class)
@SpringBootTest
public class MapperReturnTest {

    @Autowired
    private LitemallSystemMapper systemMapper;

    @Test
    public void test() {
        String pw = System.getenv("MYSQL_PASSWORD");
        Assume.assumeTrue("MYSQL_PASSWORD not set — skipping DB-backed mapper test",
                pw != null && !pw.isEmpty());

        LitemallSystem system = new LitemallSystem();
        system.setKeyName("test-system-key");
        system.setKeyValue("test-system-value");
        int updates = systemMapper.insertSelective(system);
        Assert.assertEquals(updates, 1);

        updates = systemMapper.deleteByPrimaryKey(system.getId());
        Assert.assertEquals(updates, 1);

        // The row is gone by now: an update must affect 0 rows.
        updates = systemMapper.updateByPrimaryKey(system);
        Assert.assertEquals(updates, 0);
    }

}
