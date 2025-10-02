package org.linlinjava.litemall.order.domain.model.agregates;

import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.order.application.util.exception.groupon.LitemallAlreadyJoinGrouponException;
import org.linlinjava.litemall.order.application.util.exception.groupon.LitemallCannotJoinOwnGrouponException;
import org.linlinjava.litemall.order.application.util.exception.groupon.LitemallGrouponFullException;
import org.linlinjava.litemall.order.application.util.exception.groupon.LitemallGrouponRulesNotFoundException;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallGrouponStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.GrouponParticipationInfo;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Getter
@Setter
public class LitemallGrouponRulesAggregate {


    private LitemallGrouponRulesId grouponRulesId;
    private LitemallGoodsId goodsId;

    private String goodsName;
    private String picUrl;
    private BigDecimal discount;
    private Integer discountMember;
    private LitemallGrouponStatus status;

    private LocalDateTime expireTime;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;



    public boolean isExpired() {
        return status == LitemallGrouponStatus.RULE_STATUS_DOWN_EXPIRE;
    }

    public boolean isActive (){
        return status == LitemallGrouponStatus.RULE_STATUS_ON
                && LocalDateTime.now().isBefore(expireTime);
    }

    public boolean isOfflineByAdmin() {
        return status == LitemallGrouponStatus.RULE_STATUS_DOWN_ADMIN;
    }

    public boolean canStartGroupon(){
        return isActive() && discountMember > 0;
    }


    private BigDecimal calculateGrouponPrice(BigDecimal originalPrice) {
       return originalPrice.multiply(discount).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    public void validateGrouponRules(){
        if(this.getGrouponRulesId() == null || this.getGrouponRulesId().getId() <= 0){
            throw new LitemallGrouponRulesNotFoundException("GrouponRulesId is null or less than 1.");
        }
    }

    /**
     * @Desc This method has two different value objects, so it has to be moved to a domain service.
     * @param userId
     * @param participationInfo
     */
    public void validateGrouponParticipation(LitemallUserId userId, GrouponParticipationInfo participationInfo){

        if (participationInfo.hasGrouponLink()) {

            if(participationInfo.isGrouponFull(this.getDiscountMember())){
                throw new LitemallGrouponFullException();
            }
            if(participationInfo.hasUserAlreadyJoined(userId)){
                throw new LitemallAlreadyJoinGrouponException("You have already joined this groupon.");
            }

            if(participationInfo.isCreator(userId)){
                throw new LitemallCannotJoinOwnGrouponException("You cannot join your own groupon.");
            }
        }
    }
}
