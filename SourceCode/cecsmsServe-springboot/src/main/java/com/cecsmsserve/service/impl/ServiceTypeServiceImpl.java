package com.cecsmsserve.service.impl;

import com.cecsmsserve.entity.ServiceType;
import com.cecsmsserve.mapper.ServiceTypeMapper;
import com.cecsmsserve.service.IServiceTypeService;
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
 * @since 2024-07-17
 */
@Service
public class ServiceTypeServiceImpl extends ServiceImpl<ServiceTypeMapper, ServiceType> implements IServiceTypeService {

    private final ServiceTypeMapper mapper;

    public ServiceTypeServiceImpl(ServiceTypeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CommonResult<List<ServiceType>> selectAllFather() {
        List<ServiceType> list=mapper.selectAllFather();
        CommonResult<List<ServiceType>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<ServiceType>> selectFather1() {
        List<ServiceType> list=mapper.selectFather1();
        CommonResult<List<ServiceType>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<ServiceType>> selectAllChildren() {
        List<ServiceType> list=mapper.selectAllChildren();
        CommonResult<List<ServiceType>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<ServiceType>> selectAllChildrenByFather(int id) {
        List<ServiceType> list=mapper.selectAllChildrenByFather(id);
        CommonResult<List<ServiceType>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<ServiceType>> selectChildren1ByFather(int id) {
        List<ServiceType> list=mapper.selectChildren1ByFather(id);
        CommonResult<List<ServiceType>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<Integer> insert(ServiceType serviceType) {
        serviceType.setId(null);
        int i=mapper.insert(serviceType);
        CommonResult<Integer> result=new CommonResult<>(i);
        if(i<=0){
            result.setNotInserted();
        }
        return result;
    }

    @Override
    public CommonResult<Integer> update(ServiceType serviceType) {
        int i=mapper.updateById(serviceType);
        CommonResult<Integer> result=new CommonResult<>(i);
        if(i<=0){
            result.setNotUpdate();
        }
        return result;
    }
}
