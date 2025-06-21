package org.linlinjava.litemall.wx.web;

import javax.validation.constraints.NotNull;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.wx.service.HomeCacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Category services
 */
@RestController
@RequestMapping("/wx/catalog")
@Validated
public class WxCatalogController {
    private final Log logger = LogFactory.getLog(WxCatalogController.class);

    @Autowired
    private LitemallCategoryService categoryService;
    @Autowired
    private LitemallGoodsService goodsService;

    @GetMapping("/getfirstcategory")
    public Object getFirstCategory() {
        // All first-level categories
        List<LitemallCategory> l1CatList = categoryService.queryL1();
        return ResponseUtil.ok(l1CatList);
    }

    @GetMapping("/getsecondcategory")
    public Object getSecondCategory(@NotNull Integer id) {
        // All secondary categories
        List<LitemallCategory> currentSubCategory = categoryService.queryByPid(id);
        return ResponseUtil.ok(currentSubCategory);
    }

    /**
     *Classification details
     *      @param id classification category ID.
     *      If the classification category ID is empty, select the first classification category.
     *      It should be noted that the classification category here is a first-level category
     *      @return classification details
     */
    @GetMapping("index")
    public Object index(Integer id) {

        // All first-level categories
        List<LitemallCategory> l1CatList = categoryService.queryL1();

        // Current first-level category directory
        LitemallCategory currentCategory = null;
        if (id != null) {
            currentCategory = categoryService.findById(id);
        } else {
             if (l1CatList.size() > 0) {
                currentCategory = l1CatList.get(0);
            }
        }

        // The second-level classification directory corresponding to the current
        // first-level classification directory
        List<LitemallCategory> currentSubCategory = null;
        if (null != currentCategory) {
            currentSubCategory = categoryService.queryByPid(currentCategory.getId());
        }

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("categoryList", l1CatList);
        data.put("currentCategory", currentCategory);
        data.put("currentSubCategory", currentSubCategory);
        return ResponseUtil.ok(data);
    }

    /**
     *  All classified data
     *  @return all classified data
     */
    @GetMapping("all")
    public Object queryAll() {
        //Read from cache first
        if (HomeCacheManager.hasData(HomeCacheManager.CATALOG)) {
            return ResponseUtil.ok(HomeCacheManager.getCacheData(HomeCacheManager.CATALOG));
        }


        // All first-level categories
        List<LitemallCategory> l1CatList = categoryService.queryL1();

        //List of all subcategories
        Map<Integer, List<LitemallCategory>> allList = new HashMap<>();
        List<LitemallCategory> sub;
        for (LitemallCategory category : l1CatList) {
            sub = categoryService.queryByPid(category.getId());
            allList.put(category.getId(), sub);
        }

        // Current first-level category directory
        LitemallCategory currentCategory = l1CatList.get(0);

        /**
         * The second-level classification directory corresponding4
         * to the current first-level classification directory
         */
        List<LitemallCategory> currentSubCategory = null;
        if (null != currentCategory) {
            currentSubCategory = categoryService.queryByPid(currentCategory.getId());
        }

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("categoryList", l1CatList);
        data.put("allList", allList);
        data.put("currentCategory", currentCategory);
        data.put("currentSubCategory", currentSubCategory);

        //cache data
        HomeCacheManager.loadData(HomeCacheManager.CATALOG, data);
        return ResponseUtil.ok(data);
    }

    /**
     * Current category column
     *     @param id classification category ID
     *     @return current category column
     */
    @GetMapping("current")
    public Object current(@NotNull Integer id) {
        // Current category
        LitemallCategory currentCategory = categoryService.findById(id);
        if(currentCategory == null){
            return ResponseUtil.badArgumentValue();
        }
        List<LitemallCategory> currentSubCategory = categoryService.queryByPid(currentCategory.getId());

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("currentCategory", currentCategory);
        data.put("currentSubCategory", currentSubCategory);
        return ResponseUtil.ok(data);
    }

    @GetMapping("goods")
    public Object goods(@RequestParam Integer id) {
        // Current category
        LitemallCategory currentCategory = categoryService.findById(id);
        if(currentCategory == null){
            return ResponseUtil.badArgumentValue();
        }
        List<LitemallGoods> goodsByCategory = goodsService.queryByCategory(currentCategory.getId(), 0, 80);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("currentCategory", currentCategory);
        data.put("goodsCategory", goodsByCategory);
        System.out.println("the data are: " + data);
        return ResponseUtil.ok(data);
    }


}
