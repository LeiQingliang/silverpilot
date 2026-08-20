package com.cecsmsserve.service.impl;

import com.cecsmsserve.entity.Report;
import com.cecsmsserve.mapper.ReportMapper;
import com.cecsmsserve.service.IReportService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cecsmsserve.util.result.CommonResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author GoatCode
 * @since 2024-08-20
 */
@Service
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements IReportService {

    private final ReportMapper reportMapper;

    public ReportServiceImpl(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    @Override
    public CommonResult<List<Report>> selectByuId(int uId) {
        List<Report> list=reportMapper.selectByuId(uId);
        CommonResult<List<Report>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<Report> insert(Report report) {
        report.setId(null);
        int i=reportMapper.insert(report);
        CommonResult<Report> result=new CommonResult<>(report);
        if(i<=0){
            result.setNotInserted();
        }
        return result;
    }

    @Override
    public CommonResult<Integer> update(Report report) {
        int i=reportMapper.updateById(report);
        CommonResult<Integer> result=new CommonResult<>(i);
        if(i<=0){
            result.setNotUpdate();
        }
        return result;
    }
}
