package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.core.notify.NotifyService;
import org.linlinjava.litemall.core.qcode.QCodeService;
import org.linlinjava.litemall.core.task.TaskService;
import org.linlinjava.litemall.db.domain.LitemallGroupon;
import org.linlinjava.litemall.db.domain.LitemallGrouponRules;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.order.application.LitemallIOrderService;
import org.linlinjava.litemall.order.application.util.exception.groupon.LitemallAlreadyJoinGrouponException;
import org.linlinjava.litemall.order.application.util.exception.groupon.LitemallCannotJoinOwnGrouponException;
import org.linlinjava.litemall.order.application.util.exception.groupon.LitemallGrouponFullException;
import org.linlinjava.litemall.order.application.util.exception.groupon.LitemallGrouponRulesNotFoundException;
import org.linlinjava.litemall.order.domain.model.commands.LitemallPlaceOrderCommand;
import org.linlinjava.litemall.order.domain.model.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.model.repositories.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallGrouponRulesId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallUserId;
import org.springframework.stereotype.Service;


@Service
public class LitemallOrderServiceImpl implements LitemallIOrderService {

    private final LitemallOrderRepository orderRepository;
    private final LitemallGrouponRepository grouponRepository;
    private final LitemallGrouponRulesRepository grouponRulesRepository;
    private final LitemallUserRepository userRepository;
    private final LitemallCartRepository cartRepository;
    private final LitemallCouponRepository couponRepository;
    private final LitemallProductRepository productRepository;
    private final LitemallAddressRepository addressRepository;

    private final LitemallDomainEventPublisher domainEventPublisher;
    private final QCodeService qCodeService;
    private final NotifyService notifyService;
    private final TaskService taskService;

    public LitemallOrderServiceImpl(LitemallOrderRepository orderRepo,
                                    LitemallGrouponRepository grouponRepo,
                                    LitemallGrouponRulesRepository grouponRulesRepo,
                                    LitemallUserRepository userRepo,
                                    LitemallCartRepository cartRepo,
                                    LitemallCouponRepository couponRepo,
                                    LitemallProductRepository productRepo,
                                    LitemallAddressRepository addressRepo,
                                    LitemallDomainEventPublisher domainEventPublisher,
                                    QCodeService qCodeService,
                                    NotifyService notifyService,
                                    TaskService taskService) {
        this.orderRepository = orderRepo;
        this.grouponRepository = grouponRepo;
        this.grouponRulesRepository = grouponRulesRepo;
        this.userRepository = userRepo;
        this.cartRepository = cartRepo;
        this.couponRepository = couponRepo;
        this.productRepository = productRepo;
        this.addressRepository = addressRepo;
        this.domainEventPublisher = domainEventPublisher;
        this.qCodeService = qCodeService;
        this.notifyService = notifyService;
        this.taskService = taskService;
    }


    @Override
    public LitemallOrderId placeOrder(LitemallPlaceOrderCommand command) {

        // Validate the command
        if(command.getUserId() == null){
            throw new IllegalArgumentException("User id is required.");
        }

        LitemallUserId userId = new LitemallUserId(command.getUserId());
        LitemallUser user = userRepository.findById(userId);


        //Validate and process groupon if available
        return null;
    }

    private LitemallGrouponRules validateAndGetGroupon(Integer grouponRulesId, Integer grouponLinkId, LitemallUserId userId) {

        if(grouponRulesId == null || grouponRulesId <= 0){
            return null;
        }

        LitemallGrouponRules grouponRules = grouponRulesRepository.findById(new LitemallGrouponRulesId(grouponRulesId));
        if(grouponRules == null){
            throw new LitemallGrouponRulesNotFoundException("Groupon rules not found.");
        }

        if(grouponLinkId!= null && grouponLinkId > 0){
            LitemallGrouponId grouponId = new LitemallGrouponId(grouponLinkId);

            if(grouponRepository.countByGrouponId(grouponId) >= (grouponRules.getDiscountMember() - 1)){
                throw new LitemallGrouponFullException("Invalid groupon link.");
            }

            if(grouponRepository.existsByUserIdOrGrouponId(userId, grouponId)){
                throw new LitemallAlreadyJoinGrouponException("User has already joined the groupon.");
            }

            LitemallGroupon groupon = grouponRepository.findById(grouponId);

            if(groupon.getCreatorUserId().equals(userId.getId())){
                throw new LitemallCannotJoinOwnGrouponException("You cannot join your own groupon.");
            }
        }
      return grouponRules;
    }
}
