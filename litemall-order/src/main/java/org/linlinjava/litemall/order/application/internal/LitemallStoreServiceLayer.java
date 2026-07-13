package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.db.dao.LitemallStoreMapper;
import org.linlinjava.litemall.db.domain.LitemallStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Physical pickup stores (Wave 4, Task B — V35 {@code litemall_store}). Customer reads
 * are visibility-filtered ({@code is_show=1}); the admin surface sees every non-deleted
 * store. Pickup submit validates against {@link #findPickupable(Integer)} — a hidden
 * store must reject new pickup orders but stays resolvable for historical ones.
 */
@Service
public class LitemallStoreServiceLayer {

    private final LitemallStoreMapper storeMapper;

    public LitemallStoreServiceLayer(LitemallStoreMapper storeMapper) {
        this.storeMapper = storeMapper;
    }

    /** Customer-facing list: visible, non-deleted stores. */
    public List<LitemallStore> listVisible() {
        return storeMapper.selectVisible();
    }

    /** Any non-deleted store (admin reads + historical order rendering). */
    public LitemallStore findById(Integer id) {
        return id == null ? null : storeMapper.selectById(id);
    }

    /** The store, only if a NEW pickup order may target it (exists, visible). */
    public LitemallStore findPickupable(Integer id) {
        LitemallStore store = findById(id);
        return store == null || !Boolean.TRUE.equals(store.getIsShow()) ? null : store;
    }

    // ---- admin CRUD -------------------------------------------------------------------

    public List<LitemallStore> listAll() {
        return storeMapper.selectAllAdmin();
    }

    @Transactional
    public Integer create(LitemallStore store) {
        store.setId(null);
        if (store.getIsShow() == null) {
            store.setIsShow(true);
        }
        storeMapper.insert(store);
        return store.getId();
    }

    @Transactional
    public boolean update(LitemallStore store) {
        if (store.getId() == null || storeMapper.selectById(store.getId()) == null) {
            return false;
        }
        return storeMapper.update(store) > 0;
    }

    @Transactional
    public boolean delete(Integer id) {
        return id != null && storeMapper.logicalDelete(id) > 0;
    }
}
