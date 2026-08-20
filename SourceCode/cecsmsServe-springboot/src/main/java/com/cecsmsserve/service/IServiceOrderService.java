package com.cecsmsserve.service;

import com.cecsmsserve.entity.ServiceOrder;
import com.baomidou.mybatisplus.spring.service.IService;
import com.cecsmsserve.util.result.CommonResult;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-17
 */
public interface IServiceOrderService extends IService<ServiceOrder> {

    CommonResult selectAll();

    CommonResult selectAllByPage(int current, int size);

    CommonResult selectServiceByPage(int current, int size);

    CommonResult selectHealthByPage(int current, int size);

    CommonResult selectByUId(int uId);

    CommonResult selectByuIdByState(int uId, int state);

    CommonResult selectByState(int orderState);

    CommonResult insert(ServiceOrder serviceOrder);

    CommonResult<Integer> transition(ServiceOrder serviceOrder, int expectedState);

    CommonResult<Void> cancelByUser(Integer orderId, Integer userId);

    CommonResult countRate();
}
