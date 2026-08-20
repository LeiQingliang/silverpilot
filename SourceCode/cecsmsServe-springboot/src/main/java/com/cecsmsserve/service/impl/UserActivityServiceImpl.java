package com.cecsmsserve.service.impl;

import com.cecsmsserve.entity.Activity;
import com.cecsmsserve.entity.UserActivity;
import com.cecsmsserve.entity.vo.MyActivity;
import com.cecsmsserve.entity.vo.SignedUserList;
import com.cecsmsserve.mapper.ActivityMapper;
import com.cecsmsserve.mapper.UserActivityMapper;
import com.cecsmsserve.mapper.UserMapper;
import com.cecsmsserve.service.IUserActivityService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cecsmsserve.util.result.CommonResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-22
 */
@Service
public class UserActivityServiceImpl extends ServiceImpl<UserActivityMapper, UserActivity> implements IUserActivityService {

    private final UserActivityMapper mapper;
    private final ActivityMapper activityMapper;
    private final UserMapper userMapper;

    public UserActivityServiceImpl(
            UserActivityMapper mapper,
            ActivityMapper activityMapper,
            UserMapper userMapper) {
        this.mapper = mapper;
        this.activityMapper = activityMapper;
        this.userMapper = userMapper;
    }

    @Override
    public CommonResult<List<UserActivity>> selectAll() {
        List<UserActivity> list=mapper.selectAll();
        CommonResult<List<UserActivity>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<UserActivity>> selectAllByPage(int current, int size) {
        current = normalizeCurrent(current);
        size = normalizeSize(size);
        int start=(current-1)*size;
        List<UserActivity> list=mapper.selectAllByPage(start,size);
        long total=mapper.selectCount(null);
        CommonResult<List<UserActivity>> result=new CommonResult<>(String.valueOf(total),list);
        return result;
    }

    @Override
    public CommonResult<List<MyActivity>> selectAllByuId(int uId) {
        List<MyActivity> list=mapper.selectAllByuId(uId);
        CommonResult<List<MyActivity>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<MyActivity>> selectByUIdBymyState(int uId, String state) {
        List<MyActivity> list=mapper.selectByUIdBymyState(uId,state);
        CommonResult<List<MyActivity>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<UserActivity> selectByuIdByaId(int uId, int aId) {
        UserActivity userActivity=mapper.selectByuIdByaId(uId,aId);
        CommonResult<UserActivity> result=new CommonResult<>(userActivity);
        if(userActivity==null){
            result.setNotFound();
        }
        return result;
    }

    public List<SignedUserList> selectUserList(int aId){
        List<Integer> list = mapper.selectUserByaId(aId);
        List<SignedUserList> userList = new ArrayList<>();
        for (Integer id : list) {
            SignedUserList user = userMapper.selectUserList(id);
            if (user != null) {
                userList.add(user);
            }
        }
        return userList;
    }

    @Override
    public CommonResult<List<SignedUserList>> selectUserByaId(int aId) {
        List<SignedUserList> userList = selectUserList(aId);
        return CommonResult.success(userList);
    }

    @Override
    public CommonResult<List<MyActivity>> selectByUIdByState(int uId, String state) {
        List<MyActivity> list=mapper.selectByUIdByState(uId,state);
        CommonResult<List<MyActivity>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    @Transactional
    public CommonResult<UserActivity> insert(UserActivity userActivity) {
        CommonResult<UserActivity> result = new CommonResult<>();
        if (userActivity == null || userActivity.getuId() == null || userActivity.getaId() == null) {
            return CommonResult.validateFailed("用户和活动编号不能为空");
        }
        userActivity.setId(null);
        userActivity.setEnterDate(java.time.LocalDate.now());
        userActivity.setEnterTime(java.time.LocalTime.now().withNano(0));
        int activityId = userActivity.getaId();
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null || mapper.selectByuIdByaId(userActivity.getuId(), activityId) != null) {
            result.setNotInserted();
            result.setMsg(activity == null ? "活动不存在" : "请勿重复报名");
            return result;
        }
        if (activityMapper.claimAvailableSlot(activityId) <= 0) {
            result.setNotInserted();
            result.setMsg("活动已截止或名额已满");
            return result;
        }
        userActivity.setState("报名成功");
        if (mapper.insert(userActivity) <= 0) {
            throw new IllegalStateException("Failed to save activity registration");
        }
        result.setOK(userActivity);
        return result;
    }

    @Override
    @Transactional
    public CommonResult<Integer> cancel(UserActivity userActivity) {
        if (userActivity == null || userActivity.getId() == null
                || userActivity.getuId() == null || userActivity.getaId() == null) {
            return CommonResult.validateFailed("报名记录信息不完整");
        }
        int updated = mapper.cancelConfirmed(
                userActivity.getId(), userActivity.getuId(), userActivity.getaId());
        if (updated != 1) {
            return CommonResult.validateFailed("报名状态已变更，请刷新后重试");
        }
        if (activityMapper.releaseClaimedSlot(userActivity.getaId()) != 1) {
            throw new IllegalStateException("Registration count is inconsistent with the confirmed registration");
        }
        return CommonResult.success(updated, "已取消报名");
    }

    private int normalizeCurrent(int current) {
        return Math.max(current, 1);
    }

    private int normalizeSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }
}
