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
        if(status == LitemallGrouponStatus.RULE_STATUS_DOWN_EXPIRE) {
            return true;
        }
        return false;
    }

    public boolean isOffline() {
        if (status == LitemallGrouponStatus.RULE_STATUS_DOWN_ADMIN) {
            return true;
        }
        return false;
    }

    public void validateGrouponRules(){
        if(this.getGrouponRulesId() == null || this.getGrouponRulesId().getId() <= 0){
            throw new LitemallGrouponRulesNotFoundException("GrouponRulesId is null or less than 1.");
        }
    }


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
