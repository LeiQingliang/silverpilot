package com.cecsmsserve.controller;

import com.cecsmsserve.service.IActivityService;
import com.cecsmsserve.service.IServiceOrderService;
import com.cecsmsserve.service.IUserService;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.result.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

@RestController
@RequestMapping("/count")
public class CountController {

    private final IServiceOrderService serviceOrderService;
    private final IUserService userService;
    private final IActivityService activityService;

    public CountController(
            IServiceOrderService serviceOrderService,
            IUserService userService,
            IActivityService activityService) {
        this.serviceOrderService = serviceOrderService;
        this.userService = userService;
        this.activityService = activityService;
    }

    @GetMapping("/getSum/{roleId}")
    public CommonResult<?> getSum(@PathVariable int roleId, HttpServletRequest request) {
        if (roleId < 1 || roleId > 4) {
            return CommonResult.validateFailed("用户角色无效");
        }
        return staffOnly(request, () -> userService.getSum(roleId));
    }

    @GetMapping("/getUsersSum")
    public CommonResult<?> getUsersSum(HttpServletRequest request) {
        return staffOnly(request, userService::getUsersSum);
    }

    @GetMapping("/getDoctorSum")
    public CommonResult<?> getDoctorSum(HttpServletRequest request) {
        return staffOnly(request, userService::getDoctorSum);
    }

    @GetMapping("/getWorkerSum")
    public CommonResult<?> getWorkerSum(HttpServletRequest request) {
        return staffOnly(request, userService::getWorkerSum);
    }

    @GetMapping("/countRate")
    public CommonResult<?> countRate(HttpServletRequest request) {
        return staffOnly(request, serviceOrderService::countRate);
    }

    @GetMapping("/countSignedUpNum")
    public CommonResult<?> countSignedUpNum(HttpServletRequest request) {
        return staffOnly(request, activityService::countSignedUpNum);
    }

    @GetMapping("/countActivitySort")
    public CommonResult<?> countActivitySort(HttpServletRequest request) {
        return staffOnly(request, activityService::countActivitySort);
    }

    private CommonResult<?> staffOnly(HttpServletRequest request, Supplier<CommonResult<?>> action) {
        Object value = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        return value instanceof Integer roleId && roleId >= 1 && roleId <= 3
                ? action.get()
                : CommonResult.forbidden();
    }
}
