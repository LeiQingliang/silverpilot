package com.cecsmsserve.controller;

import com.cecsmsserve.service.ISysFunctionService;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.result.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sysFunction")
public class SysFunctionController {

    private final ISysFunctionService service;

    public SysFunctionController(ISysFunctionService service) {
        this.service = service;
    }

    @GetMapping("/selectAll")
    public CommonResult<?> selectAll(HttpServletRequest request) {
        return currentRoleId(request) == 1 ? service.selectAll() : CommonResult.forbidden();
    }

    @GetMapping("/selectByRid/{rid}")
    public CommonResult<?> selectByRoleId(@PathVariable int rid, HttpServletRequest request) {
        Integer currentRole = currentRoleId(request);
        if (currentRole == null) {
            return CommonResult.unauthorized();
        }
        if (rid < 1 || rid > 4) {
            return CommonResult.validateFailed("用户角色无效");
        }
        if (currentRole != 1 && currentRole != rid) {
            return CommonResult.forbidden();
        }
        return service.selectByRid(rid);
    }

    private Integer currentRoleId(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        return value instanceof Integer roleId ? roleId : null;
    }
}
