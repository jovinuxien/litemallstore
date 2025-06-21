package org.linlinjava.litemall.goods.interfaces.rest;


import com.github.pagehelper.PageInfo;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.goods.application.LitemallGoodsManagementService;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallCategoryAggregate;
import org.linlinjava.litemall.goods.domain.model.agregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.category.LitemallCategoryId;
import org.linlinjava.litemall.goods.domain.model.valueobjects.manufacturer.LitemallManufacturerId;
import org.linlinjava.litemall.goods.infrastructure.configuration.RabbitMqConfig;
import org.linlinjava.litemall.goods.infrastructure.messaging.source.MessageProducer;
import org.linlinjava.litemall.goods.interfaces.api.category.LitemallCategoryServiceApi;
import org.linlinjava.litemall.goods.interfaces.api.goods.LitemallGoodsServiceApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/goods")
public class LitemallGoodsController {


    @Autowired
    private LitemallGoodsServiceApi goodsServiceApi;
    @Autowired
    private LitemallCategoryServiceApi categoryServiceApi;
    @Autowired
    private LitemallGoodsManagementService goodsManagementService;
    
    @Autowired
    private MessageProducer messageProducer;


    @GetMapping("/list")
    public Object listGoods(
            Integer categoryId,
            Integer brandId,
            String keyword,
            Boolean isNew,
            Boolean isHot,
            // @LoginUser Integer userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit,
            @Sort(accepts = {"add_time", "retail_price", "name"}) @RequestParam(defaultValue = "add_time") String sort,
            @Order @RequestParam(defaultValue = "desc") String order
    ) {
        LitemallCategoryId catId = new LitemallCategoryId(categoryId);
        LitemallManufacturerId manufacturerId = new LitemallManufacturerId(brandId);

        List<LitemallGoodsAggregate> goodsList = goodsServiceApi.getGoodsBySelective(catId, manufacturerId, keyword, isHot, isNew, page, limit, sort);

        System.out.println("the goodsList are: " + goodsList);
        PageInfo<LitemallGoodsAggregate> pagedList = PageInfo.of(goodsList);

        Map<String, Object> entity = new HashMap<>();
        entity.put("list", goodsList);
        entity.put("total", pagedList.getTotal());
        entity.put("page", pagedList.getPageNum());
        entity.put("limit", pagedList.getPageSize());
        entity.put("pages", pagedList.getPages());

        return ResponseUtil.ok(entity);
    }

    @GetMapping("by-category")
    public Object goodsByCategory(@RequestParam Integer id) {

        LitemallCategoryId categoryId = new LitemallCategoryId(id);
        // Current category
        LitemallCategoryAggregate currentCategory = categoryServiceApi.getCategoryById(categoryId);
        if(currentCategory == null){
            return ResponseUtil.badArgumentValue();
        }
        List<LitemallGoodsAggregate> goodsByCategory = goodsServiceApi.getGoodsByCategoryId(currentCategory.getCategoryId(), 0, 80);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("currentCategory", currentCategory);
        data.put("goodsCategory", goodsByCategory);
        System.out.println("the data are: " + data);
        return ResponseUtil.ok(data);
    }

    /**
     * Product details page "Everyone is watching" recommended products
     *
     * @param id, 商品ID
     * @return Recommended products on product details page
     */
    @GetMapping("related")
    public Object related(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        LitemallGoodsAggregate goods = goodsServiceApi.getGoodsById(goodsId);
        if (goods == null) {
            return ResponseUtil.badArgumentValue();
        }

        // The current product recommendation algorithm only recommends other products of the same category.
        LitemallCategoryId cid = new LitemallCategoryId(goods.getCategoryId().getId());

        // Find six related products
        int related = 6;
        List<LitemallGoodsAggregate> goodsList = goodsServiceApi.getGoodsByCategoryId(cid, 0, related);
        return ResponseUtil.okList(goodsList);
    }


    @GetMapping("/datail")
    public Object privateGoodsDetails(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        return goodsManagementService.goodsDetail(goodsId);
    }


    @GetMapping("/messages")
    //public Object sendMessage(@NotNull String message) {
    public Object sendMessage(@RequestParam @NotBlank String message) {
        messageProducer.sendMessage(
                RabbitMqConfig.EXCHANGE_NAME,
                RabbitMqConfig.QUEUE_NAME,
                message
        );

        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        return data;
    }

   /* @PreAuthorize("hasAuthority('SCOPE_NICE')")
    @GetMapping("/ping")
    public Object ping() {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        Map<String, Object> data = new HashMap<>();
        System.out.println("the scopes are: " + authentication.getAuthorities() + "  --  " + authentication.getPrincipal());
        data.put("scopes", authentication.getAuthorities());
        return data;
    }*/

    @PreAuthorize("hasAuthority('account.view-profile')")
    @GetMapping("/ping")
    //@RolesAllowed({"NICE"})
    //public Object getPing(JwtAuthenticationToken auth) {
    public Object getPing(Principal principal) {
       /* return new UserInfoDto(
                auth.getToken().getClaimAsString(StandardClaimNames.PREFERRED_USERNAME));
                //auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
                //auth.getAuthorities().stream().map  // convert to list));*/
      Map<String, Object> data = new HashMap<>();
      data.put("hello", "world");
      data.put("principal username", principal.getName());
      return data;
    }


    @GetMapping("/goodsdetail")
    public Object getGoodsDetail(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        return goodsManagementService.getGoodsAggregateById(goodsId);
    }

    @GetMapping("/product")
    public Object getGoodsProductByGoodsId(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        return goodsManagementService.getGoodsProductAggregateByGoodsId(goodsId);
    }

    @GetMapping("/attribute")
    public Object getGoodsAttributeByGoodsId(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        return goodsManagementService.getGoodsAttributeAggregateByGoodsId(goodsId);
    }

    @GetMapping("/specification")
    public Object getGoodsSpecificationsByGoodsId(@NotNull Integer id) {
        LitemallGoodsId goodsId = new LitemallGoodsId(id);
        return goodsManagementService.getGoodsSpecificationAggregateByGoodsId(goodsId);
    }
}
