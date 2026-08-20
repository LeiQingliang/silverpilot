package com.cecsmsserve.service;

import com.cecsmsserve.entity.UserActivity;
import com.baomidou.mybatisplus.spring.service.IService;
import com.cecsmsserve.entity.vo.SignedUserList;
import com.cecsmsserve.util.result.CommonResult;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-22
 */
public interface IUserActivityService extends IService<UserActivity> {

    CommonResult selectAll();

    CommonResult selectAllByPage(int current, int size);

    CommonResult selectAllByuId(int uId);

    CommonResult selectByUIdByState(int uId, String state);

    CommonResult selectByUIdBymyState(int uId, String state);

    CommonResult selectByuIdByaId(int uId, int aId);

    List<SignedUserList> selectUserList(int aId);

    CommonResult selectUserByaId(int aId);

    CommonResult insert(UserActivity userActivity);

    CommonResult<Integer> cancel(UserActivity userActivity);
}
