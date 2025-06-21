package org.linlinjava.litemall.goods.interfaces.rest;


import org.linlinjava.litemall.goods.interfaces.api.brand.LitemallBrandServiceApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/goods/manufacturer")
public class LitemallManufactureController {

    private LitemallBrandServiceApi brandServiceApi;

}
