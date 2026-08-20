package com.cecsmsserve.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cecsmsserve.entity.RecipeOrder;
import com.cecsmsserve.util.result.CommonResult;

public interface IRecipeOrderService extends IService<RecipeOrder> {
    CommonResult<RecipeOrder> placeOrder(RecipeOrder order);
    CommonResult<IPage<RecipeOrder>> getMyOrders(
            Integer userId, Integer current, Integer size, String recipeName, Integer status);
    CommonResult<IPage<RecipeOrder>> getAllOrders(
            Integer current, Integer size, String recipeName, Integer status);
    CommonResult<Void> updateOrderStatus(Integer id, Integer status, Integer userId);
    CommonResult<Void> cancelOrder(Integer id, Integer userId);
    CommonResult<RecipeOrder> getOrderDetail(Integer id, Integer userId);
}
