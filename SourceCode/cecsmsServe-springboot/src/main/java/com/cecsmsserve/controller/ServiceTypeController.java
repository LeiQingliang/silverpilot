package com.cecsmsserve.controller;

import com.cecsmsserve.entity.ServiceType;
import com.cecsmsserve.service.IServiceTypeService;
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
@RequestMapping("/serviceType")
public class ServiceTypeController {

    private final IServiceTypeService service;

    public ServiceTypeController(IServiceTypeService service) {
        this.service = service;
    }

    @GetMapping("/selectAllFather")
    public CommonResult<?> selectAllParents() {
        return service.selectAllFather();
    }

    @GetMapping("/selectFather1")
    public CommonResult<?> selectActiveParents() {
        return service.selectFather1();
    }

    @GetMapping("/selectAllChildren")
    public CommonResult<?> selectAllChildren() {
        return service.selectAllChildren();
    }

    @GetMapping("/selectAllChildrenByFather/{id}")
    public CommonResult<?> selectAllChildrenByParent(@PathVariable int id) {
        return id > 0
                ? service.selectAllChildrenByFather(id)
                : CommonResult.validateFailed("服务分类编号无效");
    }

    @GetMapping("/selectChildren1ByFather/{id}")
    public CommonResult<?> selectActiveChildrenByParent(@PathVariable int id) {
        return id > 0
                ? service.selectChildren1ByFather(id)
                : CommonResult.validateFailed("服务分类编号无效");
    }

    @PutMapping("/insert")
    public CommonResult<?> insert(@RequestBody ServiceType type, HttpServletRequest request) {
        if (!isServiceManager(request)) {
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
    public CommonResult<?> update(@RequestBody ServiceType type, HttpServletRequest request) {
        if (!isServiceManager(request)) {
            return CommonResult.forbidden();
        }
        String error = validate(type, true);
        if (error != null) {
            return CommonResult.validateFailed(error);
        }
        if (service.getById(type.getId()) == null) {
            return CommonResult.notFound("服务分类不存在");
        }
        return service.update(type);
    }

    private String validate(ServiceType type, boolean requireId) {
        if (type == null) {
            return "服务分类不能为空";
        }
        if (requireId && (type.getId() == null || type.getId() <= 0)) {
            return "服务分类编号无效";
        }
        if (type.getServiceName() == null || type.getServiceName().isBlank()
                || type.getServiceName().trim().length() > 100) {
            return "服务分类名称不能为空且不能超过100字";
        }
        if (type.getState() == null || (type.getState() != 0 && type.getState() != 1)) {
            return "服务分类状态无效";
        }
        if (type.getLeaderId() != null) {
            ServiceType parent = service.getById(type.getLeaderId());
            if (parent == null || parent.getLeaderId() != null) {
                return "上级服务分类不存在";
            }
        }
        if (type.getImage() != null && type.getImage().length() > 500) {
            return "图片地址不能超过500字";
        }
        type.setServiceName(type.getServiceName().trim());
        return null;
    }

    private boolean isServiceManager(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        return value instanceof Integer roleId && (roleId == 1 || roleId == 2);
    }
}
