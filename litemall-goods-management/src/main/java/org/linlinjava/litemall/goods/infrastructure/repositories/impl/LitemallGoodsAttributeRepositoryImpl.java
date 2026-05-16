package org.linlinjava.litemall.goods.infrastructure.repositories.impl;

import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallGoodsAttributeMapper;
import org.linlinjava.litemall.db.domain.LitemallGoodsAttribute;
import org.linlinjava.litemall.db.domain.LitemallGoodsAttributeExample;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAttributeAggregate;
import org.linlinjava.litemall.goods.domain.model.repositories.LitemallGoodsAttributeRepository;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsAttributeId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;


@Repository
public class LitemallGoodsAttributeRepositoryImpl implements LitemallGoodsAttributeRepository {

    @Resource
    private LitemallGoodsAttributeMapper goodsAttributeMapper;

    @Override
    public void save(LitemallGoodsAttributeAggregate goodsAttributeAggregate) {
      LitemallGoodsAttribute record = convertToDataModel(goodsAttributeAggregate);
      record.setAddTime(LocalDateTime.now());
      record.setUpdateTime(LocalDateTime.now());
      goodsAttributeMapper.insertSelective(record);
    }

    @Override
    public int updateById(LitemallGoodsAttributeAggregate goodsAttributeAggregate) {
        LitemallGoodsAttribute attribute = convertToDataModel(goodsAttributeAggregate);
        attribute.setUpdateTime(LocalDateTime.now());
        return goodsAttributeMapper.updateByPrimaryKeySelective(attribute);
    }

    @Override
    public void removeById(LitemallGoodsAttributeId goodsAttributeId) {
        LitemallGoodsAttributeExample example = new LitemallGoodsAttributeExample();
        example.or().andGoodsIdEqualTo(Integer.parseInt(goodsAttributeId.getId()));
        goodsAttributeMapper.logicalDeleteByExample(example);
    }

    @Override
    public void removeByGoodsId(LitemallGoodsId good) {
        LitemallGoodsAttributeExample example = new LitemallGoodsAttributeExample();
        example.or().andGoodsIdEqualTo(good.getId());
        goodsAttributeMapper.logicalDeleteByExample(example);
    }

    @Override
    public List<LitemallGoodsAttributeAggregate> queryByGoodsId(LitemallGoodsId goodsId) {
        LitemallGoodsAttributeExample example = new LitemallGoodsAttributeExample();
        example.or().andGoodsIdEqualTo(goodsId.getId()).andDeletedEqualTo(false);
        return goodsAttributeMapper.selectByExample(example).stream().map(this::convertToDomainModel).toList();
    }

    @Override
    public LitemallGoodsAttributeAggregate queryById(LitemallGoodsAttributeId id) {
       return convertToDomainModel(goodsAttributeMapper.selectByPrimaryKey(Integer.parseInt(id.getId())));
    }


    public LitemallGoodsAttributeAggregate convertToDomainModel(LitemallGoodsAttribute record) {

        if(record == null){
            return null;
        }
        LitemallGoodsAttributeAggregate goodsAttributeAggregate = new LitemallGoodsAttributeAggregate();

        // Relationship mappings
        goodsAttributeAggregate.setGoodsAttributeId(new LitemallGoodsAttributeId(record.getId().toString()));
        goodsAttributeAggregate.setGoodsId(new LitemallGoodsId(record.getGoodsId()));

        goodsAttributeAggregate.setAttributeName(record.getAttribute());
        goodsAttributeAggregate.setAttributeValue(record.getValue());

        goodsAttributeAggregate.setAddTime(record.getAddTime());
        goodsAttributeAggregate.setUpdateTime(record.getUpdateTime());
        goodsAttributeAggregate.setDeleted(record.getDeleted());



        return goodsAttributeAggregate;
    }

    public LitemallGoodsAttribute convertToDataModel(LitemallGoodsAttributeAggregate goodsAttributeAggregate) {

        LitemallGoodsAttribute dataModel = new LitemallGoodsAttribute();

        if(goodsAttributeAggregate.getGoodsAttributeId() != null){
            dataModel.setId(Integer.parseInt(goodsAttributeAggregate.getGoodsAttributeId().getId()));
        }
        dataModel.setGoodsId(goodsAttributeAggregate.getGoodsId().getId());

        dataModel.setAttribute(goodsAttributeAggregate.getAttributeValue());
        dataModel.setValue(goodsAttributeAggregate.getAttributeName());


        dataModel.setAddTime(goodsAttributeAggregate.getAddTime());
        dataModel.setUpdateTime(goodsAttributeAggregate.getUpdateTime());
        dataModel.setDeleted(goodsAttributeAggregate.isDeleted());

        return dataModel;
    }
}
