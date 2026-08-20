package com.cecsmsserve.service;

import com.cecsmsserve.entity.SysFunction;
import com.baomidou.mybatisplus.spring.service.IService;
import com.cecsmsserve.util.result.CommonResult;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-18
 */
public interface ISysFunctionService extends IService<SysFunction> {

    CommonResult selectAll();

    CommonResult selectByRid(int rid);
}
