package org.linlinjava.litemall.db.dao;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallCjSyncRun;

/**
 * Hand-written mapper for CJ pipeline run bookkeeping ({@code litemall_cj_sync_run}, V45).
 * No {@code Example} machinery — only what the Wave-12 inventory flow's wire tap needs.
 */
public interface LitemallCjSyncRunMapper {

    /** Open a run row (started_time stamped by the caller); generated id lands on the bean. */
    int insertRun(LitemallCjSyncRun run);

    /** Close a run row: finished_time, counts, complete flag, optional error. */
    int finishRun(LitemallCjSyncRun run);

    /** Latest runs, newest first; phase null = all phases. */
    List<LitemallCjSyncRun> selectRecent(@Param("phase") String phase, @Param("limit") int limit);
}
