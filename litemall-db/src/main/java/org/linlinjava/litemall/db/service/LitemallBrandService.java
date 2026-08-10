package org.linlinjava.litemall.db.service;

import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallBrandMapper;
import org.linlinjava.litemall.db.domain.LitemallBrand;
import org.linlinjava.litemall.db.domain.LitemallBrandExample;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallBrandService {
    @Resource
    private LitemallBrandMapper brandMapper;
    @Resource
    private org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper linkageMapper;
    private LitemallBrand.Column[] columns = new LitemallBrand.Column[]{LitemallBrand.Column.id, LitemallBrand.Column.name, LitemallBrand.Column.desc, LitemallBrand.Column.picUrl, LitemallBrand.Column.floorPrice, LitemallBrand.Column.kind};

    public List<LitemallBrand> query(Integer page, Integer limit, String sort, String order) {
        LitemallBrandExample example = new LitemallBrandExample();
        example.or().andDeletedEqualTo(false);
        if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }
        PageHelper.startPage(page, limit);
        return brandMapper.selectByExampleSelective(example, columns);
    }

    /**
     * Public storefront listing: only rows an admin has display-enabled (V60 curation gate).
     * Provider-captured rows (raw supplier legal-entity names) stay hidden until curated.
     */
    public List<LitemallBrand> queryDisplayEnabled(Integer page, Integer limit, String sort, String order) {
        LitemallBrandExample example = new LitemallBrandExample();
        example.or().andDeletedEqualTo(false).andDisplayEnabledEqualTo(true);
        if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }
        PageHelper.startPage(page, limit);
        return brandMapper.selectByExampleSelective(example, columns);
    }

    public List<LitemallBrand> query(Integer page, Integer limit) {
        return query(page, limit, null, null);
    }

    public LitemallBrand findById(Integer id) {
        return brandMapper.selectByPrimaryKey(id);
    }

    public List<LitemallBrand> querySelective(String id, String name, Integer page, Integer size, String sort, String order) {
        LitemallBrandExample example = new LitemallBrandExample();
        LitemallBrandExample.Criteria criteria = example.createCriteria();

        if (!StringUtils.isEmpty(id)) {
            criteria.andIdEqualTo(Integer.valueOf(id));
        }
        if (!StringUtils.isEmpty(name)) {
            criteria.andNameLike("%" + name + "%");
        }
        criteria.andDeletedEqualTo(false);

        if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }

        PageHelper.startPage(page, size);
        return brandMapper.selectByExample(example);
    }



    public int updateById(LitemallBrand brand) {
        brand.setUpdateTime(LocalDateTime.now());
        return brandMapper.updateByPrimaryKeySelective(brand);
    }

    public void deleteById(Integer id) {
        brandMapper.logicalDeleteByPrimaryKey(id);
    }

    public void add(LitemallBrand brand) {
        brand.setAddTime(LocalDateTime.now());
        brand.setUpdateTime(LocalDateTime.now());
        brandMapper.insertSelective(brand);
    }

    public List<LitemallBrand> all() {
        LitemallBrandExample example = new LitemallBrandExample();
        example.or().andDeletedEqualTo(false);
        return brandMapper.selectByExample(example);
    }

    /**
     * Attach the computed on-sale goods count to each row (one grouped query, in place — the
     * PageHelper page object is preserved so okList totals stay correct). Rows without goods get 0.
     */
    public void attachGoodsCounts(List<LitemallBrand> brands) {
        if (brands == null || brands.isEmpty()) {
            return;
        }
        List<Integer> ids = brands.stream().map(LitemallBrand::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : linkageMapper.countOnSaleGoodsByBrand(ids)) {
            Object brandId = row.get("brandId");
            Object goodsCount = row.get("goodsCount");
            if (brandId instanceof Number b && goodsCount instanceof Number c) {
                counts.put(b.intValue(), c.intValue());
            }
        }
        for (LitemallBrand brand : brands) {
            brand.setGoodsCount(counts.getOrDefault(brand.getId(), 0));
        }
    }
}
