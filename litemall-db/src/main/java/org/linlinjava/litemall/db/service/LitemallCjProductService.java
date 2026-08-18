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

    /**
     * A 1-based page of live snapshot rows, newest first — the paginated list-read source so a list
     * surface pages the DB instead of re-hitting the CJ API. Page/size are clamped to sane bounds.
     */
    public List<LitemallCjProduct> queryLivePaged(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * safeSize;
        return cjProductMapper.selectLivePaged(offset, safeSize);
    }

    /** Count of live (non-deleted) snapshot rows — paging total. */
    public int countLive() {
        return cjProductMapper.countLive();
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

    /** Write the enriched detail/inventory fields onto an existing row + stamp enriched_time. */
    public int enrich(LitemallCjProduct row) {
        return cjProductMapper.enrich(row);
    }

    /** Live rows due for detail+inventory enrichment (never-enriched first, then oldest), capped. */
    public List<LitemallCjProduct> queryForEnrichment(int limit) {
        return cjProductMapper.selectForEnrichment(limit);
    }

    /** Wave 26: CJ explicitly denied this pid — count a delisting strike. */
    public int recordDelistedStrike(String pid) {
        return cjProductMapper.recordDelistedStrike(pid);
    }

    /** A successful detail fetch clears the strikes. */
    public int clearDelistedStrikes(String pid) {
        return cjProductMapper.clearDelistedStrikes(pid);
    }

    /** Pids CJ has denied at least {@code minStrikes} times. */
    public java.util.List<String> delistedPids(int minStrikes) {
        return cjProductMapper.selectDelistedPids(minStrikes);
    }

    /** Wave 26 Phase 1b: EU-capture coverage {enrichedTotal, probedTotal, euStocked}. */
    public java.util.Map<String, Object> euCoverage() {
        return cjProductMapper.selectEuCoverage();
    }

    /** Wave 26 Phase 1b: per-L1 EU survival over on-sale CJ goods, with probed denominators. */
    /** Wave 27: pids with a measured non-zero EU warehouse reading — the {@code eu_flag} basis. */
    public java.util.List<String> euStockedPids() {
        return cjProductMapper.selectEuStockedPids();
    }

    public java.util.List<java.util.Map<String, Object>> euSurvivalByRoot() {
        return cjProductMapper.selectEuSurvivalByRoot();
    }

    /** Supplier-attribution coverage over enriched rows: {enrichedTotal, supplierPopulated}. */
    public java.util.Map<String, Object> supplierCoverage() {
        return cjProductMapper.selectSupplierCoverage();
    }

    /** Soft-delete rows whose pids are no longer present upstream; returns rows affected. */
    public int softDelete(List<String> pids) {
        if (pids == null || pids.isEmpty()) {
            return 0;
        }
        return cjProductMapper.softDeleteByPids(pids);
    }
}
