package com.cecsmsserve.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cecsmsserve.entity.RecipeOrder;
import com.cecsmsserve.entity.User;
import com.cecsmsserve.mapper.RecipeOrderMapper;
import com.cecsmsserve.service.IRecipeOrderService;
import com.cecsmsserve.service.IUserService;
import com.cecsmsserve.util.result.CommonResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;

@Service
public class RecipeOrderServiceImpl
        extends ServiceImpl<RecipeOrderMapper, RecipeOrder>
        implements IRecipeOrderService {

    private final RecipeOrderMapper recipeOrderMapper;
    private final IUserService userService;

    public RecipeOrderServiceImpl(RecipeOrderMapper recipeOrderMapper, IUserService userService) {
        this.recipeOrderMapper = recipeOrderMapper;
        this.userService = userService;
    }

    @Override
    @Transactional
    public CommonResult<RecipeOrder> placeOrder(RecipeOrder order) {
        if (order == null || order.getUserId() == null || order.getRecipeId() == null) {
            return CommonResult.validateFailed("用户和菜谱信息不能为空");
        }
        if (order.getOrderTime() == null || !order.getOrderTime().toInstant().isAfter(Instant.now())) {
            return CommonResult.validateFailed("预订时间必须晚于当前时间");
        }
        if (order.getPeopleCount() == null || order.getPeopleCount() < 1 || order.getPeopleCount() > 20) {
            return CommonResult.validateFailed("用餐人数需为1-20人");
        }
        if (order.getRemark() != null && order.getRemark().length() > 500) {
            return CommonResult.validateFailed("备注不能超过500个字符");
        }

        Date now = new Date();
        order.setId(null);
        order.setStatus(0);
        order.setCompleteTime(null);
        order.setCreateTime(now);
        order.setUpdateTime(now);
        order.setRecipeName(null);
        order.setRecipeImageUrl(null);
        order.setUserName(null);
        return recipeOrderMapper.insert(order) == 1
                ? CommonResult.success(order, "预订成功")
                : CommonResult.failed("预订失败");
    }

    @Override
    @Transactional(readOnly = true)
    public CommonResult<IPage<RecipeOrder>> getMyOrders(
            Integer userId, Integer current, Integer size, String recipeName, Integer status) {
        if (userId == null) {
            return CommonResult.unauthorized();
        }
        String validationError = validateFilters(recipeName, status);
        if (validationError != null) {
            return CommonResult.validateFailed(validationError);
        }
        Page<RecipeOrder> page = new Page<>(normalizeCurrent(current), normalizeSize(size));
        IPage<RecipeOrder> result = recipeOrderMapper.selectUserOrders(
                page, userId, normalizeText(recipeName), status);
        return CommonResult.success(result);
    }

    @Override
    @Transactional(readOnly = true)
    public CommonResult<IPage<RecipeOrder>> getAllOrders(
            Integer current, Integer size, String recipeName, Integer status) {
        String validationError = validateFilters(recipeName, status);
        if (validationError != null) {
            return CommonResult.validateFailed(validationError);
        }
        Page<RecipeOrder> page = new Page<>(normalizeCurrent(current), normalizeSize(size));
        IPage<RecipeOrder> result = recipeOrderMapper.selectAllOrders(
                page, normalizeText(recipeName), status);
        return CommonResult.success(result);
    }

    @Override
    @Transactional
    public CommonResult<Void> updateOrderStatus(Integer id, Integer status, Integer currentUserId) {
        if (id == null || id <= 0 || currentUserId == null) {
            return CommonResult.validateFailed("预订或操作人编号无效");
        }
        if (status == null || (status != 1 && status != 2)) {
            return CommonResult.validateFailed("只能将待处理预订标记为已完成或已取消");
        }
        RecipeOrder order = recipeOrderMapper.selectById(id);
        if (order == null) {
            return CommonResult.notFound("预订记录不存在");
        }
        User currentUser = userService.getById(currentUserId);
        if (currentUser == null) {
            return CommonResult.unauthorized("当前用户不存在");
        }

        boolean manager = currentUser.getRoleId() != null
                && (currentUser.getRoleId() == 1 || currentUser.getRoleId() == 3);
        if (status == 1 && !manager) {
            return CommonResult.forbidden("仅管理人员可完成预订");
        }
        if (!manager && !currentUserId.equals(order.getUserId())) {
            return CommonResult.forbidden("无权更新此预订");
        }
        if (!Integer.valueOf(0).equals(order.getStatus())) {
            return CommonResult.validateFailed("当前状态不允许再次处理");
        }
        return recipeOrderMapper.updateOrderStatus(id, status) == 1
                ? CommonResult.success(null, "更新状态成功")
                : CommonResult.validateFailed("预订状态已变更，请刷新后重试");
    }

    @Override
    @Transactional
    public CommonResult<Void> cancelOrder(Integer id, Integer userId) {
        if (id == null || id <= 0 || userId == null) {
            return CommonResult.validateFailed("预订或用户编号无效");
        }
        RecipeOrder order = recipeOrderMapper.selectById(id);
        if (order == null) {
            return CommonResult.notFound("预订记录不存在");
        }
        if (!userId.equals(order.getUserId())) {
            return CommonResult.forbidden("无权取消此预订");
        }
        if (!Integer.valueOf(0).equals(order.getStatus())) {
            return CommonResult.validateFailed("当前状态不允许取消");
        }
        return recipeOrderMapper.updateOrderStatus(id, 2) == 1
                ? CommonResult.success(null, "取消预订成功")
                : CommonResult.validateFailed("预订状态已变更，请刷新后重试");
    }

    @Override
    @Transactional(readOnly = true)
    public CommonResult<RecipeOrder> getOrderDetail(Integer id, Integer userId) {
        if (id == null || id <= 0 || userId == null) {
            return CommonResult.validateFailed("预订或用户编号无效");
        }
        RecipeOrder order = recipeOrderMapper.selectById(id);
        if (order == null) {
            return CommonResult.notFound("预订记录不存在");
        }
        return userId.equals(order.getUserId())
                ? CommonResult.success(order)
                : CommonResult.forbidden("无权查看此预订详情");
    }

    private long normalizeCurrent(Integer current) {
        return current == null || current < 1 ? 1 : current;
    }

    private long normalizeSize(Integer size) {
        return size == null || size < 1 ? 10 : Math.min(size, 100);
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String validateFilters(String recipeName, Integer status) {
        if (recipeName != null && recipeName.trim().length() > 100) {
            return "菜谱名称搜索不能超过100个字符";
        }
        if (status != null && (status < 0 || status > 2)) {
            return "预订状态无效";
        }
        return null;
    }
}
