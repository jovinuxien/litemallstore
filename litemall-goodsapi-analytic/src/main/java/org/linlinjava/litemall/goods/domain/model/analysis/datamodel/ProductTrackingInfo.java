package org.linlinjava.litemall.goods.domain.model.analysis.datamodel;

import lombok.Getter;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.product.CJProduct;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;


@Getter
public class ProductTrackingInfo {

    private final CJProduct cjProduct;
    private  int appearanceCount;
    private  int disappearanceCount;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private LocalDateTime lastDisappearance;

    public ProductTrackingInfo(CJProduct cjProduct){
        this.cjProduct = cjProduct;
        this.appearanceCount = 1;
        this.firstSeen = LocalDateTime.now();
        this.lastSeen = LocalDateTime.now();
        this.lastDisappearance = LocalDateTime.now();
    }

    public void recordAppearance(LocalDateTime timestamp) {
        appearanceCount++;
        lastSeen = timestamp;
        disappearanceCount = 0; // Reset disappearance count
    }
    public void recordDisappearance() {
        disappearanceCount++;
        lastDisappearance = LocalDateTime.now();
    }

    public boolean shouldRemoveFromTracking() {
        return isDisappeared() &&
                ChronoUnit.DAYS.between(lastDisappearance, LocalDateTime.now()) > 30;
    }

    public boolean isDisappeared() {
        return disappearanceCount > 0;
    }

}
