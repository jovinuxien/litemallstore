package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.dto.ElasticDto;

import java.util.List;

public interface LitemallElasticMapper {
    List<ElasticDto> selectGoodsWithJoin(@Param("manufacturerName") String manufacturerName,
                                         @Param("attribute") String attribute);
}
