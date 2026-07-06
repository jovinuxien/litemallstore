package org.linlinjava.litemall.order.infrastructure.repositories.impl;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallCartRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.*;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class LitemallCartRepositoryImpl implements LitemallCartRepository {

    private final LitemallCartMapper cartMapper;
    
    public LitemallCartRepositoryImpl(LitemallCartMapper cartMapper) {
        this.cartMapper = cartMapper;
    }

    @Override
    public List<LitemallCartAggregate> findCheckedByUserId(LitemallUserId userId) {
        LitemallCartExample example = new LitemallCartExample();
        example.or().andUserIdEqualTo(userId.getId())
                .andCheckedEqualTo(true).andDeletedEqualTo(false);
        return cartMapper.selectByExample(example).stream().map(this::convertToDomainModel)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isGoodsInAlreadyInCart(LitemallUserId userId, LitemallGoodsId goodsId, LitemallGoodsProductId productId) {
        return false;
    }

    @Override
    public void addNewCart(LitemallCartAggregate cart) {
        LitemallCart cartdata = this.convertToDataModel(cart);
        cartdata.setAddTime(LocalDateTime.now());
        cartdata.setUpdateTime(LocalDateTime.now());
        cartMapper.insertSelective(cartdata);
    }


    @Override
    public List<LitemallCartAggregate> findByUserId(LitemallUserId userId) {
        LitemallCartExample example = new LitemallCartExample();
        example.or().andIdEqualTo(userId.getId()).andDeletedEqualTo(false);
        return cartMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public LitemallCartAggregate findById(LitemallCartId cartId) {
        LitemallCartExample example = new LitemallCartExample();
        example.or().andIdEqualTo(cartId.getId()).andCheckedEqualTo(true).andDeletedEqualTo(false);
        return convertToDomainModel(cartMapper.selectOneByExample(example));
    }

    @Override
    public void clearCheckedByUserId(LitemallUserId userId) {
        LitemallCartExample example = new LitemallCartExample();
        example.or().andUserIdEqualTo(userId.getId()).andCheckedEqualTo(true);
        LitemallCart cart = new LitemallCart();
        cart.setDeleted(true);
        cartMapper.updateByExampleSelective(cart, example);
    }

    @Override
    public int updateCheck(LitemallUserId userId, List<LitemallGoodsProductId> productIdList, boolean checked) {

        LitemallCartExample example = new LitemallCartExample();
        List<Integer> idsList = new ArrayList<>();
        for(LitemallGoodsProductId goodsProductId: productIdList){
            idsList.add(goodsProductId.getId());
        }
        example.or().andUserIdEqualTo(userId.getId()).andProductIdIn(idsList).andDeletedEqualTo(false);
        LitemallCart cart = new LitemallCart();
        cart.setChecked(checked);
        cart.setUpdateTime(LocalDateTime.now());
        return cartMapper.updateByExampleSelective(cart, example);
    }

    @Override
    public void deleteById(LitemallCartId id) {
        cartMapper.logicalDeleteByPrimaryKey(id.getId());
    }

    @Override
    public LitemallCartAggregate findByUserIdAndGoodsId(LitemallUserId userId, LitemallGoodsId goodsId, LitemallGoodsProductId productId) {
        LitemallCartExample example = new LitemallCartExample();
        example.or().andGoodsIdEqualTo(goodsId.getId()).andProductIdEqualTo(productId.getId()).andUserIdEqualTo(userId.getId()).andDeletedEqualTo(false);
        return convertToDomainModel(cartMapper.selectOneByExample(example));
    }

    @Override
    public List<LitemallCartAggregate> findAllActiveByUserId(LitemallUserId userId) {
        LitemallCartExample example = new LitemallCartExample();
        example.or().andUserIdEqualTo(userId.getId()).andDeletedEqualTo(false);
        return cartMapper.selectByExample(example).stream().map(this::convertToDomainModel).collect(Collectors.toList());
    }

    @Override
    public LitemallCartAggregate findActiveById(LitemallCartId id) {
        LitemallCartExample example = new LitemallCartExample();
        example.or().andIdEqualTo(id.getId()).andDeletedEqualTo(false);
        return convertToDomainModel(cartMapper.selectOneByExample(example));
    }

    @Override
    public void update(LitemallCartAggregate cart) {
        LitemallCart record = convertToDataModel(cart);
        record.setUpdateTime(LocalDateTime.now());
        cartMapper.updateByPrimaryKeySelective(record);
    }

    @Override
    public void clearAllByUserId(LitemallUserId userId) {
        LitemallCartExample example = new LitemallCartExample();
        example.or().andUserIdEqualTo(userId.getId()).andDeletedEqualTo(false);
        LitemallCart cart = new LitemallCart();
        cart.setDeleted(true);
        cart.setUpdateTime(LocalDateTime.now());
        cartMapper.updateByExampleSelective(cart, example);
    }

    /**
     *    --------- Block of utility methods -----------------
     */
    public LitemallCart convertToDataModel(LitemallCartAggregate cartAggregate) {
        LitemallCart dataModel = new LitemallCart();

        // A freshly-built cart line (legacy /srv/cart/add, /cart/items) has no cartId
        // yet — leave the PK unset so insertSelective auto-increments. Only carry an
        // explicit id through on update. (Previously this NPE'd on every brand-new line.)
        if(cartAggregate.getCartId() != null && cartAggregate.getCartId().getId() != null){
            dataModel.setId(cartAggregate.getCartId().getId());
        }
        dataModel.setUserId(cartAggregate.getUserId().getId());
        dataModel.setProductId(cartAggregate.getProductId().getId());
        dataModel.setGoodsId(cartAggregate.getGoodsId().getId());

        dataModel.setGoodsSn(cartAggregate.getGoodsSn());
        dataModel.setGoodsName(cartAggregate.getGoodsName());
        dataModel.setPrice(cartAggregate.getPrice().getAmount());
        dataModel.setNumber(cartAggregate.getNumber().shortValue());
        dataModel.setSpecifications(cartAggregate.getSpecifications());
        dataModel.setChecked(cartAggregate.isChecked());
        dataModel.setPicUrl(cartAggregate.getPicUrl());

        dataModel.setAddTime(cartAggregate.getAddTime());
        dataModel.setUpdateTime(cartAggregate.getUpdateTime());
        dataModel.setDeleted(cartAggregate.isDeleted());

        //Other fields should be completed
        return dataModel;
    }

    public LitemallCartAggregate convertToDomainModel(LitemallCart record) {

        if(record == null){
            return null;
        }

        LitemallCartAggregate domainModel = new LitemallCartAggregate();

        // Relationship mappings
        domainModel.setCartId(new LitemallCartId(record.getId()));
        domainModel.setUserId(new LitemallUserId(record.getUserId()));
        domainModel.setGoodsId(new LitemallGoodsId(record.getGoodsId()));
        domainModel.setProductId(new LitemallGoodsProductId(record.getProductId()));


        domainModel.setGoodsSn(record.getGoodsSn());
        domainModel.setGoodsName(record.getGoodsName());
        domainModel.setPicUrl(record.getPicUrl());
        domainModel.setPrice(new LitemallMoney(record.getPrice()));
        domainModel.setNumber(record.getNumber().intValue());
        domainModel.setSpecifications(record.getSpecifications());
        domainModel.setChecked(record.getChecked());
        domainModel.setUpdateTime(record.getUpdateTime());
        domainModel.setAddTime(record.getAddTime());
        domainModel.setDeleted(record.getDeleted());
        // Other fields should be completed

        return  domainModel;
    }
}
