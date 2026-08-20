package com.cecsmsserve.controller;

import com.cecsmsserve.entity.ServiceOrder;
import com.cecsmsserve.entity.ServiceType;
import com.cecsmsserve.service.IServiceOrderService;
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

import java.time.LocalDate;
import java.time.LocalTime;

@RestController
@RequestMapping("/serviceOrder")
public class ServiceOrderController {

    private final IServiceOrderService service;
    private final IServiceTypeService serviceTypeService;

    public ServiceOrderController(IServiceOrderService service, IServiceTypeService serviceTypeService) {
        this.service = service;
        this.serviceTypeService = serviceTypeService;
    }

    @GetMapping("/selectAll")
    public CommonResult<?> selectAll(HttpServletRequest request) {
        return hasAnyRole(request, 1, 2, 3) ? service.selectAll() : CommonResult.forbidden();
    }

    @GetMapping("/selectAllByPage/{current}/{size}")
    public CommonResult<?> selectAllByPage(
            @PathVariable int current,
            @PathVariable int size,
            HttpServletRequest request) {
        return hasAnyRole(request, 1, 2, 3)
                ? service.selectAllByPage(current, size)
                : CommonResult.forbidden();
    }

    @GetMapping("/selectServiceByPage/{current}/{size}")
    public CommonResult<?> selectServiceByPage(
            @PathVariable int current,
            @PathVariable int size,
            HttpServletRequest request) {
        return hasAnyRole(request, 1, 2)
                ? service.selectServiceByPage(current, size)
                : CommonResult.forbidden();
    }

    @GetMapping("/selectHealthByPage/{current}/{size}")
    public CommonResult<?> selectHealthByPage(
            @PathVariable int current,
            @PathVariable int size,
            HttpServletRequest request) {
        return hasAnyRole(request, 1, 3)
                ? service.selectHealthByPage(current, size)
                : CommonResult.forbidden();
    }

    @GetMapping("/selectByUId/{uId}")
    public CommonResult<?> selectByUId(@PathVariable int uId, HttpServletRequest request) {
        if (!mayReadUser(request, uId)) {
            return CommonResult.forbidden();
        }
        return service.selectByUId(uId);
    }

    @GetMapping("/selectByuIdByState/{uId}/{state}")
    public CommonResult<?> selectByUIdByState(
            @PathVariable int uId,
            @PathVariable int state,
            HttpServletRequest request) {
        if (!mayReadUser(request, uId)) {
            return CommonResult.forbidden();
        }
        if (state < 0 || state > 4) {
            return CommonResult.validateFailed("订单状态无效");
        }
        return service.selectByuIdByState(uId, state);
    }

    @GetMapping("/selectByState/{state}")
    public CommonResult<?> selectByState(@PathVariable int state, HttpServletRequest request) {
        if (!hasAnyRole(request, 1, 2, 3)) {
            return CommonResult.forbidden();
        }
        if (state < 0 || state > 4) {
            return CommonResult.validateFailed("订单状态无效");
        }
        return service.selectByState(state);
    }

    @PutMapping("/insert")
    public CommonResult<?> insert(@RequestBody ServiceOrder requestBody, HttpServletRequest request) {
        Integer userId = currentUserId(request);
        if (userId == null) {
            return CommonResult.unauthorized();
        }
        String validationError = validateNewOrder(requestBody);
        if (validationError != null) {
            return CommonResult.validateFailed(validationError);
        }

        ServiceOrder order = new ServiceOrder();
        order.setuId(userId);
        order.setTypeBId(requestBody.getTypeBId());
        order.setTypeSId(requestBody.getTypeSId());
        order.setReserveDate(requestBody.getReserveDate());
        order.setServiceAddress(requestBody.getServiceAddress().trim());
        order.setOrderDetail(trimToNull(requestBody.getOrderDetail()));
        order.setOrderDate(LocalDate.now());
        order.setOrderTime(LocalTime.now().withNano(0));
        return service.insert(order);
    }

    @PostMapping("/update")
    public CommonResult<?> update(@RequestBody ServiceOrder requestBody, HttpServletRequest request) {
        if (requestBody.getId() == null || requestBody.getId() <= 0 || requestBody.getOrderState() == null) {
            return CommonResult.validateFailed("订单编号和目标状态不能为空");
        }
        ServiceOrder existing = service.getById(requestBody.getId());
        if (existing == null) {
            return CommonResult.notFound("订单不存在");
        }

        Integer userId = currentUserId(request);
        Integer roleId = currentRoleId(request);
        if (userId == null || roleId == null) {
            return CommonResult.unauthorized();
        }
        if (roleId == 4) {
            return updateByOwner(existing, requestBody, userId);
        }
        return updateByManager(existing, requestBody, userId, roleId);
    }

    private CommonResult<?> updateByOwner(ServiceOrder existing, ServiceOrder requestBody, Integer userId) {
        if (!userId.equals(existing.getuId())) {
            return CommonResult.forbidden("只能修改自己的订单");
        }

        ServiceOrder update = new ServiceOrder();
        update.setId(existing.getId());
        int targetState = requestBody.getOrderState();
        if (existing.getOrderState() == 0 && targetState == 1) {
            return service.cancelByUser(existing.getId(), userId);
        } else if (existing.getOrderState() == 2 && targetState == 3) {
            update.setOrderState(3);
            update.setFinishDate(LocalDate.now());
            update.setFinishTime(LocalTime.now().withNano(0));
        } else if (existing.getOrderState() == 3 && targetState == 4) {
            Double rate = requestBody.getRate();
            if (rate == null || rate < 1 || rate > 5) {
                return CommonResult.validateFailed("评分必须在1到5之间");
            }
            update.setRate(rate);
            update.setOrderState(4);
        } else {
            return CommonResult.validateFailed("当前订单状态不允许该操作");
        }
        return service.transition(update, existing.getOrderState());
    }

    private CommonResult<?> updateByManager(
            ServiceOrder existing,
            ServiceOrder requestBody,
            Integer userId,
            Integer roleId) {
        boolean healthOrder = Integer.valueOf(4).equals(existing.getTypeBId());
        boolean allowed = roleId == 1 || (healthOrder && roleId == 3) || (!healthOrder && roleId == 2);
        if (!allowed) {
            return CommonResult.forbidden("没有该类订单的受理权限");
        }
        if (existing.getOrderState() != 0 || requestBody.getOrderState() != 2) {
            return CommonResult.validateFailed("只能受理待受理订单");
        }

        String workerName = trimToNull(requestBody.getName());
        String workerPhone = trimToNull(requestBody.getTelephone());
        if (workerName == null || workerName.length() > 50) {
            return CommonResult.validateFailed("请填写有效的服务人员姓名");
        }
        if (workerPhone == null || !workerPhone.matches("^[0-9+ -]{6,20}$")) {
            return CommonResult.validateFailed("请填写有效的服务人员电话");
        }

        ServiceOrder update = new ServiceOrder();
        update.setId(existing.getId());
        update.setOrderState(2);
        update.setAcceptDate(LocalDate.now());
        update.setAcceptTime(LocalTime.now().withNano(0));
        update.setName(workerName);
        update.setTelephone(workerPhone);
        if (healthOrder) {
            update.setdId(userId);
        } else {
            update.setmId(userId);
        }
        return service.transition(update, 0);
    }

    private String validateNewOrder(ServiceOrder order) {
        if (order.getTypeBId() == null || order.getTypeSId() == null) {
            return "请选择完整的服务类别";
        }
        ServiceType parent = serviceTypeService.getById(order.getTypeBId());
        ServiceType child = serviceTypeService.getById(order.getTypeSId());
        if (parent == null || child == null || !Integer.valueOf(1).equals(parent.getState())
                || !Integer.valueOf(1).equals(child.getState())
                || !parent.getId().equals(child.getLeaderId())) {
            return "服务类别不存在或已停用";
        }
        if (order.getReserveDate() == null || order.getReserveDate().isBefore(LocalDate.now())) {
            return "预约日期不能早于今天";
        }
        String address = trimToNull(order.getServiceAddress());
        if (address == null || address.length() > 255) {
            return "服务地点不能为空且不能超过255字";
        }
        String detail = trimToNull(order.getOrderDetail());
        if (detail != null && detail.length() > 255) {
            return "预约详情不能超过255字";
        }
        return null;
    }

    private boolean mayReadUser(HttpServletRequest request, int requestedUserId) {
        Integer current = currentUserId(request);
        return current != null && (current == requestedUserId || hasAnyRole(request, 1, 2, 3));
    }

    private boolean hasAnyRole(HttpServletRequest request, int... roles) {
        Integer current = currentRoleId(request);
        if (current == null) {
            return false;
        }
        for (int role : roles) {
            if (current == role) {
                return true;
            }
        }
        return false;
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ID_ATTRIBUTE);
        return value instanceof Integer id ? id : null;
    }

    private Integer currentRoleId(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        return value instanceof Integer id ? id : null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
