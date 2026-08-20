package com.cecsmsserve.service;

import com.cecsmsserve.entity.ServiceType;
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
public interface IServiceTypeService extends IService<ServiceType> {

    CommonResult selectAllFather();

    CommonResult selectFather1();

    CommonResult selectAllChildren();

    CommonResult selectAllChildrenByFather(int id);

    CommonResult selectChildren1ByFather(int id);

    CommonResult insert(ServiceType serviceType);

    CommonResult update(ServiceType serviceType);
}
