package com.cecsmsserve.service;

import com.cecsmsserve.entity.ActivityType;
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
public interface IActivityTypeService extends IService<ActivityType> {

    CommonResult selectAll();

    CommonResult selectByName(String name);

    CommonResult selectByState1();

    CommonResult insert(ActivityType activityType);

    CommonResult update(ActivityType activityType);


}
