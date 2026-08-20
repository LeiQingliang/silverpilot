package com.cecsmsserve.controller;

import com.cecsmsserve.entity.Recipe;
import com.cecsmsserve.entity.RecipeOrder;
import com.cecsmsserve.service.IRecipeOrderService;
import com.cecsmsserve.service.IRecipeService;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.result.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Date;

@RestController
@RequestMapping("/recipe-order")
public class RecipeOrderController {

    private final IRecipeOrderService orderService;
    private final IRecipeService recipeService;

    public RecipeOrderController(IRecipeOrderService orderService, IRecipeService recipeService) {
        this.orderService = orderService;
        this.recipeService = recipeService;
    }

    @PostMapping("/order")
    public CommonResult<?> placeOrder(@RequestBody RecipeOrder order, HttpServletRequest request) {
        if (order == null || order.getRecipeId() == null || order.getRecipeId() <= 0) {
            return CommonResult.validateFailed("请选择要预订的菜谱");
        }
        if (order.getOrderTime() == null || order.getOrderTime().toInstant().isBefore(Instant.now())) {
            return CommonResult.validateFailed("预订时间必须晚于当前时间");
        }
        if (order.getPeopleCount() == null || order.getPeopleCount() < 1 || order.getPeopleCount() > 20) {
            return CommonResult.validateFailed("用餐人数需为1-20人");
        }
        if (order.getRemark() != null && order.getRemark().length() > 500) {
            return CommonResult.validateFailed("备注不能超过500个字符");
        }

        CommonResult<?> recipeResult = recipeService.getRecipeDetail(order.getRecipeId());
        if (recipeResult.getCode() != 200 || !(recipeResult.getResult() instanceof Recipe recipe)
                || recipe.getStatus() == null || recipe.getStatus() != 1) {
            return CommonResult.notFound("菜谱不存在或已下架");
        }

        order.setId(null);
        order.setUserId(currentUserId(request));
        order.setStatus(0);
        order.setCompleteTime(null);
        order.setCreateTime(new Date());
        order.setRecipeName(null);
        order.setRecipeImageUrl(null);
        order.setUserName(null);
        return orderService.placeOrder(order);
    }

    @GetMapping("/my-orders")
    public CommonResult<?> getMyOrders(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "recipeName", required = false) String recipeName,
            @RequestParam(value = "status", required = false) Integer status,
            HttpServletRequest request) {
        return orderService.getMyOrders(currentUserId(request), current, size, recipeName, status);
    }

    @GetMapping("/all")
    public CommonResult<?> getAllOrders(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "recipeName", required = false) String recipeName,
            @RequestParam(value = "status", required = false) Integer status,
            HttpServletRequest request) {
        if (!isRecipeManager(request)) {
            return CommonResult.forbidden("仅管理员或医护人员可查看全部预订");
        }
        return orderService.getAllOrders(current, size, recipeName, status);
    }

    @PutMapping("/status")
    public CommonResult<?> updateOrderStatus(
            @RequestBody UpdateStatusRequest requestBody,
            HttpServletRequest request) {
        if (requestBody == null || requestBody.id() == null || requestBody.id() <= 0) {
            return CommonResult.validateFailed("预订编号无效");
        }
        if (requestBody.status() == null || requestBody.status() < 0 || requestBody.status() > 2) {
            return CommonResult.validateFailed("预订状态无效");
        }
        if (!isRecipeManager(request) && requestBody.status() != 2) {
            return CommonResult.forbidden("普通用户只能取消自己的预订");
        }
        return orderService.updateOrderStatus(requestBody.id(), requestBody.status(), currentUserId(request));
    }

    @PostMapping("/cancel/{id}")
    public CommonResult<?> cancelOrder(@PathVariable Integer id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            return CommonResult.validateFailed("预订编号无效");
        }
        return orderService.cancelOrder(id, currentUserId(request));
    }

    @GetMapping("/{id}")
    public CommonResult<?> getOrderDetail(@PathVariable Integer id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            return CommonResult.validateFailed("预订编号无效");
        }
        if (isRecipeManager(request)) {
            RecipeOrder order = orderService.getById(id);
            return order == null ? CommonResult.notFound("预订记录不存在") : CommonResult.success(order);
        }
        return orderService.getOrderDetail(id, currentUserId(request));
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ID_ATTRIBUTE);
        if (value instanceof Integer userId) {
            return userId;
        }
        throw new IllegalStateException("Authenticated user is missing from the request");
    }

    private boolean isRecipeManager(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        return value instanceof Integer roleId && (roleId == 1 || roleId == 3);
    }

    public record UpdateStatusRequest(Integer id, Integer status) {
    }
}
