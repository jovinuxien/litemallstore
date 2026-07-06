package org.linlinjava.litemall.order.domain.model.repositories;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallExtractAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallExtractStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.wallet.LitemallExtractId;

import java.util.List;
import java.util.Optional;

public interface LitemallExtractRepository {

    void add(LitemallExtractAggregate extract);

    Optional<LitemallExtractAggregate> findById(LitemallExtractId extractId);

    List<LitemallExtractAggregate> findByUserId(LitemallUserId userId);

    int updateStatus(LitemallExtractId extractId, LitemallExtractStatus status, String failMsg);
}
