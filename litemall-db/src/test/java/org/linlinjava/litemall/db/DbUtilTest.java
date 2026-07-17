package org.linlinjava.litemall.db;

import org.junit.Assume;
import org.junit.Test;
import org.linlinjava.litemall.db.util.DbUtil;

import java.io.File;

/**
 * Wave 7: the DB password was hardcoded here; it now comes from MYSQL_PASSWORD.
 *
 * <p>These tests touch a REAL database. They have never actually run, because the
 * root pom sets {@code maven.test.skip=true} reactor-wide. Task D lifts that, so
 * they must not fire blindly: {@code testBackup} shells out to mysqldump, and
 * {@code testLoad} RESETS the litemall database (per the original author's
 * warning, kept below in translation).
 *
 * <p>Both now self-skip unless MYSQL_PASSWORD is set, so CI stays green without a
 * database and a bare {@code mvn test} cannot wipe a developer's local one.
 */
public class DbUtilTest {

    private static String passwordOrSkip() {
        String pw = System.getenv("MYSQL_PASSWORD");
        Assume.assumeTrue("MYSQL_PASSWORD not set — skipping DB-touching test",
                pw != null && !pw.isEmpty());
        return pw;
    }

    @Test
    public void testBackup() {
        File file = new File("test.sql");
        DbUtil.backup(file, "litemall", passwordOrSkip(), "litemall");
    }

    // DANGER: this resets the litemall database. Deliberately NOT annotated @Test.
    // (original note: 这个测试用例会重置litemall数据库，所以比较危险，请开发者注意)
    public void testLoad() {
        File file = new File("test.sql");
        DbUtil.load(file, "litemall", passwordOrSkip(), "litemall");
    }
}
