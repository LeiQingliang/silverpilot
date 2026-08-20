package com.cecsmsserve.service;

import com.cecsmsserve.entity.User;
import com.baomidou.mybatisplus.spring.service.IService;
import com.cecsmsserve.util.result.CommonResult;


/**
 * <p>
 *  服务类
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-15
 */
public interface IUserService extends IService<User> {

    CommonResult register(User user);

    CommonResult login(String loginName, String password);

    CommonResult insert(User user);

    CommonResult update(User user);

    CommonResult selectByUsername(String username);

    CommonResult selectByRid(int rid);

    CommonResult selectByRidByPage(int rid, int current, int size);

    CommonResult selectById(int id);

    CommonResult selectByNameOrIdNum(String searchName);

    CommonResult getSum(int rId);

    CommonResult getUsersSum();

    CommonResult getDoctorSum();

    CommonResult getWorkerSum();
}
