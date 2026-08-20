package com.cecsmsserve.service.impl;

import com.cecsmsserve.entity.Activity;
import com.cecsmsserve.entity.vo.SignedUserList;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cecsmsserve.mapper.ActivityMapper;
import com.cecsmsserve.mapper.UserActivityMapper;
import com.cecsmsserve.mapper.UserMapper;
import com.cecsmsserve.service.IActivityService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cecsmsserve.util.result.CommonResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
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
public class ActivityServiceImpl extends ServiceImpl<ActivityMapper, Activity> implements IActivityService {

    private final ActivityMapper activityMapper;
    private final UserActivityMapper userActivityMapper;
    private final UserMapper userMapper;

    public ActivityServiceImpl(
            ActivityMapper activityMapper,
            UserActivityMapper userActivityMapper,
            UserMapper userMapper) {
        this.activityMapper = activityMapper;
        this.userActivityMapper = userActivityMapper;
        this.userMapper = userMapper;
    }

    @Override
    public CommonResult<List<Activity>> selectAll() {
        List<Activity> list=activityMapper.selectAll();
        CommonResult<List<Activity>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<Activity>> selectAllByPage(int current, int size) {
        current = normalizeCurrent(current);
        size = normalizeSize(size);
        int start=(current-1)*size;
        List<Activity> list=activityMapper.selectAllByPage(start,size);
        long total=activityMapper.selectCount(null);
        CommonResult<List<Activity>> result=new CommonResult<>(String.valueOf(total),list);
        return result;
    }

    @Override
    public CommonResult<List<Activity>> selectNotBegin() {
        List<Activity> list=activityMapper.selectNotBegin();
        CommonResult<List<Activity>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<Activity>> selectByName(String name) {
        List<Activity> list=activityMapper.selectByName(name);
        CommonResult<List<Activity>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<Activity>> selectByNameByPage(String name, int current, int size) {
        current = normalizeCurrent(current);
        size = normalizeSize(size);
        int start=(current-1)*size;
        List<Activity> list=activityMapper.selectByNameByPage(name,start,size);
        long total=activityMapper.selectCount(
                new LambdaQueryWrapper<Activity>().like(Activity::getActivityName, name));
        CommonResult<List<Activity>> result=new CommonResult<>(String.valueOf(total),list);
        return result;
    }

    @Override
    public CommonResult<List<Activity>> selectByState(int state) {
        List<Activity> list=activityMapper.selectByState(state);
        CommonResult<List<Activity>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<Activity>> selectByType(int type) {
        List<Activity> list=activityMapper.selectByType(type);
        CommonResult<List<Activity>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<Activity> selectById(int id) {
        Activity activity=activityMapper.selectDetailById(id);
        CommonResult<Activity> result=new CommonResult<>(activity);
        if(activity==null){
            result.setNotFound();
        }
        return result;
    }

    @Override
    public CommonResult<Activity> insert(Activity activity) {
        activity.setId(null);
        int i=activityMapper.insert(activity);
        CommonResult<Activity> result=new CommonResult<>(activity);
        if(i<=0){
            result.setNotInserted();
        }
        return result;
    }

    @Override
    public CommonResult<Integer> update(Activity activity) {
        int i=activityMapper.updateById(activity);
        CommonResult<Integer> result=new CommonResult<>(i);
        if(i<=0){
            result.setNotUpdate();
        }
        return result;
    }

    @Override
    public CommonResult<List<HashMap<String,Object>>> countSignedUpNum() {
        List<HashMap<String,Object>> list=activityMapper.countSignedUpNum();
        CommonResult<List<HashMap<String,Object>>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<HashMap<String,Object>>> countActivitySort() {
        List<HashMap<String,Object>> list=activityMapper.countActivitySort();
        CommonResult<List<HashMap<String,Object>>> result=new CommonResult<>(list);
        return result;
    }

    /**
     * 从第0分钟开始每30分钟执行一次，修改活动状态
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    @Transactional
    public void updateActivityStatus(){
        List<Activity> list = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>().in(Activity::getState, 1, 2));
        for(Activity activity:list){
            if (activity.getId() == null || activity.getActivityDate() == null
                    || activity.getStartTime() == null || activity.getEndTime() == null) {
                continue;
            }
            LocalDateTime now=LocalDateTime.now();
            LocalDateTime startTime=LocalDateTime.of(activity.getActivityDate(),activity.getStartTime());
            LocalDateTime endTime=LocalDateTime.of(activity.getActivityDate(),activity.getEndTime());
            if (startTime.isAfter(now)) {
                continue;
            }
            if (endTime.isAfter(now)) {
                activityMapper.markStartedIfDue(activity.getId());
                continue;
            }
            if (activityMapper.markFinishedIfDue(activity.getId()) == 1) {
                int activityPoints = activity.getActivityPoint() == null ? 0 : activity.getActivityPoint();
                if (activityPoints == 0) {
                    continue;
                }
                List<Integer> userIds = userActivityMapper.selectUserByaId(activity.getId());
                for (Integer id : userIds) {
                    if (userMapper.addPoints(id, activityPoints) != 1) {
                        throw new IllegalStateException("Unable to award activity points to user " + id);
                    }
                }
            }
        }
    }

    private int normalizeCurrent(int current) {
        return Math.max(current, 1);
    }

    private int normalizeSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }
}
