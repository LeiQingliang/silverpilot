package com.cecsmsserve.service.impl;

import com.cecsmsserve.entity.SysFunction;
import com.cecsmsserve.mapper.SysFunctionMapper;
import com.cecsmsserve.service.ISysFunctionService;
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
 * @since 2024-07-18
 */
@Service
public class SysFunctionServiceImpl extends ServiceImpl<SysFunctionMapper, SysFunction> implements ISysFunctionService {

    private final SysFunctionMapper mapper;

    public SysFunctionServiceImpl(SysFunctionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CommonResult<List<SysFunction>> selectAll() {
        List<SysFunction> list=mapper.selectAll();
        return new CommonResult<>(list);
    }

    @Override
    public CommonResult<List<SysFunction>> selectByRid(int rid) {
        List<SysFunction> list=mapper.selectByRid(rid);
        return new CommonResult<>(list);
    }
}
