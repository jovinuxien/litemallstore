package org.linlinjava.litemall.db.service;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallCjProductMapper;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Persistence facade for the CJ Dropshipping product snapshot, mirroring the
 * other {@code Litemall*Service} wrappers in this module so goods-management
 * depends on a service rather than the raw {@link LitemallCjProductMapper}.
 */
@Service
public class LitemallCjProductService {

    @Resource
    private LitemallCjProductMapper cjProductMapper;

    /** All live (non-deleted) snapshot rows — the OCS indexing source. */
    public List<LitemallCjProduct> queryAllLive() {
        return cjProductMapper.selectAllLive();
    }

    /** One snapshot row by RAW CJ pid (detail / order lookup), or null. */
    public LitemallCjProduct findByPid(String pid) {
        if (pid == null || pid.isBlank()) {
            return null;
        }
        return cjProductMapper.selectByPid(pid);
    }

    /** Raw pids of all live rows — used to diff against the upstream set for stale detection. */
    public List<String> queryLivePids() {
        return cjProductMapper.selectLivePids();
    }

    /** Insert or refresh a snapshot row (keyed on the raw CJ pid). */
    public int upsert(LitemallCjProduct row) {
        return cjProductMapper.upsert(row);
    }

    /** Soft-delete rows whose pids are no longer present upstream; returns rows affected. */
    public int softDelete(List<String> pids) {
        if (pids == null || pids.isEmpty()) {
            return 0;
        }
        return cjProductMapper.softDeleteByPids(pids);
    }
}
