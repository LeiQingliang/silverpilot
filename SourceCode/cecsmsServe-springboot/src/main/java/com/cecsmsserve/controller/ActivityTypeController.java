package com.cecsmsserve.controller;

import com.cecsmsserve.entity.ActivityType;
import com.cecsmsserve.service.IActivityTypeService;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.result.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activityType")
public class ActivityTypeController {

    private final IActivityTypeService service;

    public ActivityTypeController(IActivityTypeService service) {
        this.service = service;
    }

    @GetMapping("/selectAll")
    public CommonResult<?> selectAll() {
        return service.selectAll();
    }

    @GetMapping("/selectByName/{name}")
    public CommonResult<?> selectByName(@PathVariable String name) {
        return name.isBlank() || name.length() > 100
                ? CommonResult.validateFailed("活动类型名称无效")
                : service.selectByName(name.trim());
    }

    @GetMapping("/selectByState1")
    public CommonResult<?> selectActive() {
        return service.selectByState1();
    }

    @PutMapping("/insert")
    public CommonResult<?> insert(@RequestBody ActivityType type, HttpServletRequest request) {
        if (!isActivityManager(request)) {
            return CommonResult.forbidden();
        }
        if (type != null && type.getState() == null) {
            type.setState(1);
        }
        String error = validate(type, false);
        if (error != null) {
            return CommonResult.validateFailed(error);
        }
        type.setId(null);
        return service.insert(type);
    }

    @PostMapping("/update")
    public CommonResult<?> update(@RequestBody ActivityType type, HttpServletRequest request) {
        if (!isActivityManager(request)) {
            return CommonResult.forbidden();
        }
        String error = validate(type, true);
        if (error != null) {
            return CommonResult.validateFailed(error);
        }
        if (service.getById(type.getId()) == null) {
            return CommonResult.notFound("活动类型不存在");
        }
        return service.update(type);
    }

    private String validate(ActivityType type, boolean requireId) {
        if (type == null) {
            return "活动类型不能为空";
        }
        if (requireId && (type.getId() == null || type.getId() <= 0)) {
            return "活动类型编号无效";
        }
        if (type.getType() == null || type.getType().isBlank() || type.getType().trim().length() > 100) {
            return "活动类型名称不能为空且不能超过100字";
        }
        if (type.getDetail() != null && type.getDetail().length() > 255) {
            return "活动类型说明不能超过255字";
        }
        if (type.getPurpose() != null && type.getPurpose().length() > 255) {
            return "活动目的不能超过255字";
        }
        if (type.getState() == null || (type.getState() != 0 && type.getState() != 1)) {
            return "活动类型状态无效";
        }
        type.setType(type.getType().trim());
        return null;
    }

    private boolean isActivityManager(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        return value instanceof Integer roleId && (roleId == 1 || roleId == 2);
    }
}
