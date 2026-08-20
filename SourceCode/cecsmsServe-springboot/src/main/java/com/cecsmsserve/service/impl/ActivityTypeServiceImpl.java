package com.cecsmsserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cecsmsserve.entity.ActivityType;
import com.cecsmsserve.mapper.ActivityTypeMapper;
import com.cecsmsserve.service.IActivityTypeService;
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
public class ActivityTypeServiceImpl extends ServiceImpl<ActivityTypeMapper, ActivityType> implements IActivityTypeService {

    private final ActivityTypeMapper mapper;

    public ActivityTypeServiceImpl(ActivityTypeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CommonResult<List<ActivityType>> selectAll() {
        List<ActivityType> list=mapper.selectAll();
        CommonResult<List<ActivityType>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<ActivityType>> selectByName(String name) {
        LambdaQueryWrapper<ActivityType> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ActivityType::getType, name);
        List<ActivityType> list=mapper.selectList(queryWrapper);
        CommonResult<List<ActivityType>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<ActivityType>> selectByState1() {
        List<ActivityType> list=mapper.selectByState1();
        CommonResult<List<ActivityType>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<Integer> insert(ActivityType activityType) {
        int i=mapper.insert(activityType);
        CommonResult<Integer> result=new CommonResult<>(i);
        if(i<=0){
            result.setNotInserted();
        }
        return result;
    }

    @Override
    public CommonResult<Integer> update(ActivityType activityType) {
        int i=mapper.updateById(activityType);
        CommonResult<Integer> result=new CommonResult<>(i);
        if(i<=0){
            result.setNotUpdate();
        }
        return result;
    }
}
