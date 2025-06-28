package org.linlinjava.litemall.order.domain.model.valueobjects.groupon;

import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;

public class GrouponParticipationInfo {

    private final boolean grouponLinkExists;
    private final boolean grouponFull;
    private final boolean userAlreadyJoined;
    private final boolean isCreator;

    private GrouponParticipationInfo(boolean grouponLinkExists,
                                     boolean grouponFull,
                                     boolean userAlreadyJoined,
                                     boolean isCreator) {
        this.grouponLinkExists = grouponLinkExists;
        this.grouponFull = grouponFull;
        this.userAlreadyJoined = userAlreadyJoined;
        this.isCreator = isCreator;
    }

    // Factory method
    public static GrouponParticipationInfo create(boolean grouponLinkExists,
                                                  boolean grouponFull,
                                                  boolean userAlreadyJoined,
                                                  boolean isCreator) {
        return new GrouponParticipationInfo(grouponLinkExists, grouponFull,
                userAlreadyJoined, isCreator);
    }

    // Getters
    public boolean hasGrouponLink() { return grouponLinkExists; }
    public boolean isGrouponFull(int discountMember) { return grouponFull; }
    public boolean hasUserAlreadyJoined(LitemallUserId userId) { return userAlreadyJoined; }
    public boolean isCreator(LitemallUserId userId) { return isCreator; }
}
