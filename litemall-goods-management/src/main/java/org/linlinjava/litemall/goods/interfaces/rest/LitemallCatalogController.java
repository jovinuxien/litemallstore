package org.linlinjava.litemall.goods.interfaces.rest;


import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.application.goods.CatalogGoodsCountService;
import org.linlinjava.litemall.goods.application.goods.LitemallGoodsManagementService;
import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.category.LitemallCategoryId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("srv/catalog")
public class LitemallCatalogController {

    private final Logger log = LoggerFactory.getLogger(LitemallCatalogController.class);

    @Autowired
    private LitemallGoodsManagementService goodsManagementServiceApi;

    @Autowired
    private CatalogGoodsCountService catalogGoodsCountService;

    @GetMapping("/list")
    public Object category(@NotNull Integer id) {
        if(id == null) {
            System.out.println("the categoryId  is: " + id);
            return ResponseUtil.fail(404,"Category id is required");
        }
        LitemallCategoryId categoryId = new LitemallCategoryId(id);
        LitemallCategoryAggregate cur = goodsManagementServiceApi.getCategoryById(categoryId);

        if(cur == null) {
            return ResponseUtil.fail(404, "Category not found");
        }
        LitemallCategoryAggregate parent = null;
        List<LitemallCategoryAggregate> children = null;

        if (cur.getParentId() == null) {
            parent = cur;
            children = goodsManagementServiceApi.queryByPid(id);
            //cur = children.size() > 0 ? children.get(0) : cur;
            cur = (children != null && !children.isEmpty()) ? children.get(0) : cur;
        } else {
            parent = goodsManagementServiceApi.getCategoryById(new LitemallCategoryId(cur.getParentId()));
            children = goodsManagementServiceApi.queryByPid(id);
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
        List<LitemallCategoryAggregate> l1CatList = goodsManagementServiceApi.getFirstLevelCategories();
        Map<String, Object> data = new HashMap<>();
        data.put("l1CatList", l1CatList);
        return ResponseUtil.ok(data);
    }


    /*@GetMapping("/second-categories")
    public Object getSecondCategory(@NotNull Integer id) {
        // All first-level categories

        List<LitemallCategoryAggregate> l1CatList = goodsManagementServiceApi.getSecondLevelCategories(id);
        Map<String, Object> data = new HashMap<>();
        data.put("l2CatList", l1CatList);
        return ResponseUtil.ok(data);
    }*/

    @GetMapping("all")
    public Object queryAll() {

        // All first-level categories, MOST-PROMISING FIRST: ordered by on-sale goods count over
        // each root's whole subtree, so SPA sidebars can just take the first N.
        Map<Integer, Long> goodsCounts = catalogGoodsCountService.countsByRoot();
        List<LitemallCategoryAggregate> l1CatList =
                new java.util.ArrayList<>(goodsManagementServiceApi.getFirstLevelCategories());
        l1CatList.sort(java.util.Comparator.comparingLong(
                (LitemallCategoryAggregate c) -> goodsCounts.getOrDefault(
                        Integer.valueOf(c.getCategoryId().getId()), 0L)).reversed());

        //List of all subcategories
        Map<Integer, List<LitemallCategoryAggregate>> allList = new HashMap<>();
        List<LitemallCategoryAggregate> sub;
        for (LitemallCategoryAggregate category : l1CatList) {
            //sub = categoryService.queryByPid(category.getId());
            sub = goodsManagementServiceApi.queryByPid(Integer.valueOf(category.getCategoryId().getId()));
            allList.put(Integer.valueOf(category.getCategoryId().getId()), sub);
        }

        // Current first-level category directory (null when the catalog is empty —
        // the null-check below already handles it; get(0) on an empty list 502'd).
        LitemallCategoryAggregate currentCategory = l1CatList.isEmpty() ? null : l1CatList.get(0);

        /**
         * The second-level classification directory corresponding4
         * to the current first-level classification directory
         */
        List<LitemallCategoryAggregate> currentSubCategory = null;
        if (null != currentCategory) {
            //currentSubCategory = categoryService.queryByPid(currentCategory.getId());
            currentSubCategory = goodsManagementServiceApi.queryByPid(Integer.valueOf(currentCategory.getCategoryId().getId()));
        }

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("categoryList", l1CatList);
        data.put("goodsCounts", goodsCounts);
        data.put("allList", allList);
        data.put("currentCategory", currentCategory);
        data.put("currentSubCategory", currentSubCategory);

        return ResponseUtil.ok(data);
    }


    @GetMapping("current")
    public Object current(@NotNull Integer id) {
        // Current category
        //LitemallCategory currentCategory = categoryService.findById(id);
        LitemallCategoryId categoryId = new LitemallCategoryId(id);
        LitemallCategoryAggregate currentCategory = goodsManagementServiceApi.getCategoryById(categoryId);
        if(currentCategory == null){
            return ResponseUtil.badArgumentValue();
        }
        List<LitemallCategoryAggregate> currentSubCategory = goodsManagementServiceApi.queryByPid(currentCategory.getParentId());

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("currentCategory", currentCategory);
        data.put("currentSubCategory", currentSubCategory);
        return ResponseUtil.ok(data);
    }

    @GetMapping("/index")
    public Object index(Integer id){
        // All first-level categories
        List<LitemallCategoryAggregate> l1CatList = goodsManagementServiceApi.getFirstLevelCategories();

        // Current first-level category directory
        LitemallCategoryAggregate currentCategory = null;
        if (id != null) {
            var catId = new LitemallCategoryId(id);
            currentCategory = goodsManagementServiceApi.getCategoryById(catId);
        } else {
            if (l1CatList.size() > 0) {
                currentCategory = l1CatList.get(0);
            }
        }

        // The second-level classification directory corresponding to the current
        // first-level classification directory
        List<LitemallCategoryAggregate> currentSubCategory = null;
        if (null != currentCategory) {
            currentSubCategory = goodsManagementServiceApi.queryByPid(currentCategory.getParentId());
        }

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("categoryList", l1CatList);
        data.put("currentCategory", currentCategory);
        data.put("currentSubCategory", currentSubCategory);
        return ResponseUtil.ok(data);
    }
}