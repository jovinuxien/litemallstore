package org.linlinjava.litemall.db.dao;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallCategoryMargin;

/**
 * Hand-written mapper for per-L1-category margin overrides
 * ({@code litemall_category_margin}, V46; PK category_id).
 * No {@code Example} machinery. Absent row = global margin; rows are hard-deleted.
 */
public interface LitemallCategoryMarginMapper {

    /** Insert or replace the override for a root category. */
    int upsert(LitemallCategoryMargin override);

    /** Every override, root id order. */
    List<LitemallCategoryMargin> selectAll();

    /** The override for one root, or null. */
    LitemallCategoryMargin selectById(@Param("categoryId") int categoryId);

    /** Hard-delete an override. Returns affected rows (0 = none existed). */
    int deleteById(@Param("categoryId") int categoryId);
}
