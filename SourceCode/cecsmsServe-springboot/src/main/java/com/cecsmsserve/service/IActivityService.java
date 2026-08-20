package com.cecsmsserve.service;

import com.cecsmsserve.entity.Activity;
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
public interface IActivityService extends IService<Activity> {

    CommonResult selectAll();

    CommonResult selectAllByPage(int current,int size);

    CommonResult selectNotBegin();

    CommonResult selectByName(String name);

    CommonResult selectByNameByPage(String name, int current, int size);

    //根据活动状态
    CommonResult selectByState(int state);

    //根据活动类别
    CommonResult selectByType(int type);

    CommonResult selectById(int id);

    CommonResult insert(Activity activity);

    CommonResult update(Activity activity);

    CommonResult countSignedUpNum();

    CommonResult countActivitySort();
}
