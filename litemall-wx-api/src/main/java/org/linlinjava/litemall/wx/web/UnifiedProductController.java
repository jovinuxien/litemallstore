package org.linlinjava.litemall.wx.web;

import com.alibaba.druid.util.StringUtils;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallSearchHistory;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.db.service.LitemallSearchHistoryService;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.linlinjava.litemall.wx.service.UnifiedProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.remoting.rmi.RmiRegistryFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("wx/product")
public class UnifiedProductController {

    private final UnifiedProductService unifiedSearchService;
    private final LitemallGoodsService goodsService;
    private final LitemallSearchHistoryService searchHistoryService;

    @Autowired
    private LitemallCategoryService categoryService;

    public UnifiedProductController(UnifiedProductService unifiedSearchService, LitemallGoodsService goodsService, LitemallSearchHistoryService searchHistoryService) {
        this.unifiedSearchService = unifiedSearchService;
        this.goodsService = goodsService;
        this.searchHistoryService = searchHistoryService;
    }

    @GetMapping("list")
    public Object list(
            Integer categoryId,
            Integer brandId,
            String keyword,
            Boolean isNew,
            Boolean isHot,
            @LoginUser Integer userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "60") Integer limit,
            @Sort(accepts = {"add_time", "price", "name"}) @RequestParam(defaultValue = "add_time") String sort,
            @Order @RequestParam(defaultValue = "asc") String order) {

        // Save search history if needed
        if (userId != null && !StringUtils.isEmpty(keyword)) {
            LitemallSearchHistory searchHistoryVo = new LitemallSearchHistory();
            searchHistoryVo.setKeyword(keyword);
            searchHistoryVo.setUserId(userId);
            searchHistoryVo.setFrom("wx");
            searchHistoryService.save(searchHistoryVo);
        }

        // Use unified search
        Map<String, Object> result = unifiedSearchService.searchUnifiedProduct(
                categoryId, keyword, page, limit, sort, order);

        // Add additional data if needed
        List<Integer> goodsCatIds = goodsService.getCatIds(brandId, keyword, isHot, isNew);
        List<LitemallCategory> categoryList = goodsCatIds.isEmpty() ?
                new ArrayList<>(0) : categoryService.queryL2ByIds(goodsCatIds);

        //result.put("filterCategoryList", categoryList);

        return ResponseUtil.ok(result);
    }
}
