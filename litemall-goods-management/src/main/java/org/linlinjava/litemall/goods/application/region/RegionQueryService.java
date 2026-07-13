package org.linlinjava.litemall.goods.application.region;

import org.linlinjava.litemall.db.domain.LitemallRegion;
import org.linlinjava.litemall.db.service.LitemallRegionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Region cascade reads (litemall-wx-api {@code /wx/region/list} parity) served from an in-memory
 * snapshot of the {@code litemall_region} table (~3231 rows, effectively immutable seed data).
 * <p>
 * The snapshot is built lazily on first use with double-checked locking and then reused for every
 * subsequent call — after the first successful build no request touches SQL. If the build-time DB
 * read throws, nothing is cached and the exception propagates so the controller can degrade to a
 * clean 502; the next request retries the build. A legitimately empty table caches empty (fine).
 */
@Service
public class RegionQueryService {

    private final LitemallRegionService regionService;

    private volatile RegionSnapshot snapshot;

    public RegionQueryService(LitemallRegionService regionService) {
        this.regionService = regionService;
    }

    /** Direct children of {@code pid} as {id, pid, name, type, code} maps; unknown pid → empty list. */
    public List<Map<String, Object>> listChildren(Integer pid) {
        return snapshot().childrenByPid.getOrDefault(pid, Collections.emptyList());
    }

    /** Full 3-level province → city → county tree: {id, name, code, children:[...]}. */
    public List<Map<String, Object>> tree() {
        return snapshot().tree;
    }

    private RegionSnapshot snapshot() {
        RegionSnapshot local = snapshot;
        if (local == null) {
            synchronized (this) {
                local = snapshot;
                if (local == null) {
                    // If getAll() throws we cache NOTHING — the controller maps it to a 502 and the
                    // next call retries. Never publish a partial/empty-on-error snapshot.
                    local = build(regionService.getAll());
                    snapshot = local;
                }
            }
        }
        return local;
    }

    private static RegionSnapshot build(List<LitemallRegion> all) {
        Map<Integer, List<Map<String, Object>>> childrenByPid = new HashMap<>();
        // pid → child domain rows, for assembling the nested tree level by level.
        Map<Integer, List<LitemallRegion>> rowsByPid = new HashMap<>();
        for (LitemallRegion region : all) {
            childrenByPid.computeIfAbsent(region.getPid(), k -> new ArrayList<>()).add(flat(region));
            rowsByPid.computeIfAbsent(region.getPid(), k -> new ArrayList<>()).add(region);
        }

        List<Map<String, Object>> tree = new ArrayList<>();
        for (LitemallRegion province : rowsByPid.getOrDefault(0, Collections.emptyList())) {
            Map<String, Object> provinceNode = node(province);
            List<Map<String, Object>> cities = new ArrayList<>();
            for (LitemallRegion city : rowsByPid.getOrDefault(province.getId(), Collections.emptyList())) {
                Map<String, Object> cityNode = node(city);
                List<Map<String, Object>> counties = new ArrayList<>();
                for (LitemallRegion county : rowsByPid.getOrDefault(city.getId(), Collections.emptyList())) {
                    counties.add(node(county));
                }
                cityNode.put("children", Collections.unmodifiableList(counties));
                cities.add(Collections.unmodifiableMap(cityNode));
            }
            provinceNode.put("children", Collections.unmodifiableList(cities));
            tree.add(Collections.unmodifiableMap(provinceNode));
        }

        // Freeze the per-pid lists so the shared snapshot is safely immutable.
        Map<Integer, List<Map<String, Object>>> frozen = new HashMap<>();
        childrenByPid.forEach((pid, children) -> frozen.put(pid, Collections.unmodifiableList(children)));
        return new RegionSnapshot(Collections.unmodifiableMap(frozen), Collections.unmodifiableList(tree));
    }

    private static Map<String, Object> flat(LitemallRegion region) {
        Map<String, Object> map = new LinkedHashMap<>(5);
        map.put("id", region.getId());
        map.put("pid", region.getPid());
        map.put("name", region.getName());
        map.put("type", region.getType());
        map.put("code", region.getCode());
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, Object> node(LitemallRegion region) {
        Map<String, Object> map = new LinkedHashMap<>(4);
        map.put("id", region.getId());
        map.put("name", region.getName());
        map.put("code", region.getCode());
        return map;
    }

    /** Immutable point-in-time view of the region table. */
    private static final class RegionSnapshot {
        final Map<Integer, List<Map<String, Object>>> childrenByPid;
        final List<Map<String, Object>> tree;

        RegionSnapshot(Map<Integer, List<Map<String, Object>>> childrenByPid, List<Map<String, Object>> tree) {
            this.childrenByPid = childrenByPid;
            this.tree = tree;
        }
    }
}
