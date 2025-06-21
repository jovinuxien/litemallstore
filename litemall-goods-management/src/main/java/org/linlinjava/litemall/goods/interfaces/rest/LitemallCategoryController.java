package org.linlinjava.litemall.goods.interfaces.rest;


import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.interfaces.api.category.LitemallCategoryServiceApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/goods/catalog")
public class LitemallCategoryController {

    @Autowired
    private LitemallCategoryServiceApi categoryServiceApi;


    @GetMapping("/list")
    public Object category(@NotNull Integer id) {
        LitemallCategoryId categoryId = new LitemallCategoryId(id);
        LitemallCategoryAggregate cur = categoryServiceApi.getCategoryById(categoryId);

        if(cur == null) {
            return ResponseUtil.fail(404, "Category not found");
        }
        LitemallCategoryAggregate parent = null;
        List<LitemallCategoryAggregate> children = null;

        if (cur.getParentId() == 0) {
            parent = cur;
            children = categoryServiceApi.queryByPid(cur.getCategoryId());
            //cur = children.size() > 0 ? children.get(0) : cur;
            cur = (children != null && !children.isEmpty()) ? children.get(0) : cur;
        } else {
            parent = categoryServiceApi.getCategoryById(new LitemallCategoryId(cur.getParentId()));
            children = categoryServiceApi.queryByPid(new LitemallCategoryId(cur.getParentId()));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("currentCategory", cur);
        data.put("parentCategory", parent);
        data.put("brotherCategory", children);
        return ResponseUtil.ok(data);
    }

    @GetMapping("/first-categories")
    public Object getFirstCategory() {
        // All first-level categories
        List<LitemallCategoryAggregate> l1CatList = categoryServiceApi.getFirstLevelCategories();
        Map<String, Object> data = new HashMap<>();
        data.put("l1CatList", l1CatList);
        return ResponseUtil.ok(data);
    }


    @GetMapping("/second-categories")
    public Object getSecondCategory(@NotNull Integer id) {
        // All first-level categories
        List<LitemallCategoryAggregate> l1CatList = categoryServiceApi.getSecondLevelCategories();
        Map<String, Object> data = new HashMap<>();
        data.put("l2CatList", l1CatList);
        return ResponseUtil.ok(data);
    }
}
