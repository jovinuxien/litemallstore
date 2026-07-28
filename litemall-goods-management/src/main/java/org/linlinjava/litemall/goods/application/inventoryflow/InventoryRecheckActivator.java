package org.linlinjava.litemall.goods.application.inventoryflow;

import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.inventory.CJInventoryData;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drains the recheck queue, one variant per poll: fresh CJ warehouse stock lands on the SKU
 * row, and the goods' metric row is re-recorded so the insight series sees the change the
 * same day. An empty/failed inventory response changes NOTHING (indistinguishable from
 * "unknown" — never zero a deal SKU on a transient error).
 */
@Component
public class InventoryRecheckActivator {

    private static final Logger log = LoggerFactory.getLogger(InventoryRecheckActivator.class);

    private final CJProductService cjProductService;
    private final LitemallCjLinkageMapper linkageMapper;
    private final LitemallGoodsService goodsService;
    private final MarginRecorder marginRecorder;

    public InventoryRecheckActivator(CJProductService cjProductService,
                                     LitemallCjLinkageMapper linkageMapper,
                                     LitemallGoodsService goodsService,
                                     MarginRecorder marginRecorder) {
        this.cjProductService = cjProductService;
        this.linkageMapper = linkageMapper;
        this.goodsService = goodsService;
        this.marginRecorder = marginRecorder;
    }

    public void recheck(String vid) {
        try {
            List<CJInventoryData> inventory = cjProductService.getInventory(vid);
            if (inventory == null || inventory.isEmpty()) {
                log.debug("deal recheck: no inventory data for vid {} (left unchanged)", vid);
                return;
            }
            int sum = 0;
            boolean any = false;
            for (CJInventoryData area : inventory) {
                if (area.getStorageNum() != null) {
                    sum += area.getStorageNum();
                    any = true;
                }
            }
            if (!any) {
                return;
            }
            linkageMapper.updateProductStockByCjVid(vid, sum);
            Integer goodsId = linkageMapper.findGoodsIdByCjVid(vid);
            if (goodsId != null) {
                LitemallGoods goods = goodsService.findById(goodsId);
                if (goods != null && goods.getCjPid() != null) {
                    marginRecorder.recordByPid(goods.getCjPid());
                }
            }
        } catch (RuntimeException ex) {
            log.warn("deal recheck: vid {} failed: {}", vid, ex.getMessage());
        }
    }
}
