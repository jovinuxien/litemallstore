package org.linlinjava.litemall.goods.application.inventoryflow;

import org.linlinjava.litemall.db.dao.LitemallCjSyncRunMapper;
import org.linlinjava.litemall.db.domain.LitemallCjSyncRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Wire-tap bookkeeping (Fisher et al. ch14) for the CJ pipeline: one
 * {@code litemall_cj_sync_run} row per phase execution. Every method swallows its own
 * failures — bookkeeping must never break the pipeline it observes.
 */
@Component
public class CjSyncRunRecorder {

    private static final Logger log = LoggerFactory.getLogger(CjSyncRunRecorder.class);

    private static final int ERROR_MAX = 1023;

    private final LitemallCjSyncRunMapper mapper;

    public CjSyncRunRecorder(LitemallCjSyncRunMapper mapper) {
        this.mapper = mapper;
    }

    /** Open a run row; returns its id, or null when bookkeeping itself failed. */
    public Integer open(String phase) {
        try {
            LitemallCjSyncRun run = new LitemallCjSyncRun();
            run.setPhase(phase);
            run.setStartedTime(LocalDateTime.now());
            mapper.insertRun(run);
            return run.getId();
        } catch (RuntimeException ex) {
            log.warn("sync-run bookkeeping: open({}) failed: {}", phase, ex.getMessage());
            return null;
        }
    }

    /** Close a run row with counts + outcome; a null id (failed open) is a no-op. */
    public void close(Integer id, int upserted, int inserted, int updated, int removed,
                      boolean complete, String error) {
        if (id == null) {
            return;
        }
        try {
            LitemallCjSyncRun run = new LitemallCjSyncRun();
            run.setId(id);
            run.setFinishedTime(LocalDateTime.now());
            run.setUpserted(upserted);
            run.setInserted(inserted);
            run.setUpdated(updated);
            run.setRemoved(removed);
            run.setComplete(complete);
            run.setError(error == null ? null
                    : error.length() > ERROR_MAX ? error.substring(0, ERROR_MAX) : error);
            mapper.finishRun(run);
        } catch (RuntimeException ex) {
            log.warn("sync-run bookkeeping: close({}) failed: {}", id, ex.getMessage());
        }
    }
}
