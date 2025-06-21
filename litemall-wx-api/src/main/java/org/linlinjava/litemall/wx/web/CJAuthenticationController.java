package org.linlinjava.litemall.wx.web;


/*import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.authentication.CJAuthenticationRequest;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.authentication.CJAuthenticationResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.CJAuthenticationService;*/
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/wx/cjdropship")
@Validated
public class CJAuthenticationController {

   /* @Autowired
    private CJAuthenticationService authenticationService;

    @Autowired
    private CJAuthenticationRequest request */;

    @PostMapping("/access-token")
    public Object cjAccessToken() {
    //public Object cjAccessToken(@RequestBody CJAuthenticationRequest request) {
        //authenticationService.accessToken(request.getEmail(), request.getCjApiKey());


      /*  CJAuthenticationResponse cjResponse = authenticationService.getResponseAuthAccessData(request.getEmail());

        Map<String, Object> data = new HashMap<>();

        if (request.getEmail() == null || request.getCjApiKey() == null) {
            return ResponseUtil.badArgument();
        }

        data.put("code",  cjResponse.getCode());
        data.put("result",cjResponse.getResult());
        data.put("message", cjResponse.getMessage());
        data.put("cjData", cjResponse.getData());*/
        //data.put("access_token", authenticationService.getAccessToken(request.getEmail()));
        //return ResponseUtil.ok(data);
        return ResponseUtil.ok();
    }

    @PostMapping("/refresh-token")
    public String getAccessToken(@RequestParam String email) {
        //return authenticationService.getAccessToken(email);
        return "";
    }
}
