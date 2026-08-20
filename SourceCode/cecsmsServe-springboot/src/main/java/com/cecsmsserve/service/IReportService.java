package com.cecsmsserve.service;

import com.cecsmsserve.entity.Report;
import com.baomidou.mybatisplus.spring.service.IService;
import com.cecsmsserve.util.result.CommonResult;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author GoatCode
 * @since 2024-08-20
 */
public interface IReportService extends IService<Report> {

    CommonResult selectByuId(int uId);

    CommonResult insert(Report report);

    CommonResult update(Report report);
}
