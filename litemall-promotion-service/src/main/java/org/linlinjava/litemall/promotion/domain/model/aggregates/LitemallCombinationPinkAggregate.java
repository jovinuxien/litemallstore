package org.linlinjava.litemall.promotion.domain.model.aggregates;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallCombinationPinkId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCombinationPinkStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One participant slot of a group-buy group (crmeb {@code StorePink}). The
 * leader's slot carries {@code headId = null}; members reference the leader's
 * slot id. {@code requiredMembers}/{@code expireTime} are start-time snapshots
 * of the campaign rules. Backed by litemall-db
 * {@code litemall_combination_pink} (V30).
 */
@Getter
@Setter
@Builder
public class LitemallCombinationPinkAggregate {

    private LitemallCombinationPinkId pinkId;
    private LitemallCombinationId combinationId;
    /** Leader slot id; null when this slot IS the leader. */
    private LitemallCombinationPinkId headId;
    private LitemallUserId userId;
    private Integer orderId;
    private Integer requiredMembers;
    private LocalDateTime expireTime;
    private LitemallCombinationPinkStatus status;

    @Builder.Default
    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();

    public boolean isLeader() {
        return this.headId == null;
    }

    public boolean isPending() {
        return LitemallCombinationPinkStatus.PENDING.equals(this.status);
    }

    public boolean isExpired(LocalDateTime now) {
        return this.expireTime != null && now.isAfter(this.expireTime);
    }

    public boolean isOwnedBy(LitemallUserId candidate) {
        return this.userId != null && this.userId.equals(candidate);
    }

    /** The group filled in time. */
    public void complete() {
        if (!isPending()) {
            throw new IllegalStateException("Group is not pending.");
        }
        this.status = LitemallCombinationPinkStatus.SUCCESS;
    }

    /** The group expired (or was cancelled) before filling. */
    public void fail() {
        if (!isPending()) {
            throw new IllegalStateException("Group is not pending.");
        }
        this.status = LitemallCombinationPinkStatus.FAILED;
    }

    public void addDomainEvent(LitemallDomainEvent event) {
        this.domainEvents.add(event);
    }

    public List<LitemallDomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }
}
