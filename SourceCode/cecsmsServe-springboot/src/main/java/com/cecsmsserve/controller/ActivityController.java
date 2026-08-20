package com.cecsmsserve.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cecsmsserve.entity.Activity;
import com.cecsmsserve.entity.ActivityType;
import com.cecsmsserve.entity.User;
import com.cecsmsserve.entity.UserActivity;
import com.cecsmsserve.service.IActivityService;
import com.cecsmsserve.service.IActivityTypeService;
import com.cecsmsserve.service.IUserActivityService;
import com.cecsmsserve.service.IUserService;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.result.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/activity")
public class ActivityController {

    private final IActivityService service;
    private final IUserActivityService userActivityService;
    private final IActivityTypeService activityTypeService;
    private final IUserService userService;

    public ActivityController(
            IActivityService service,
            IUserActivityService userActivityService,
            IActivityTypeService activityTypeService,
            IUserService userService) {
        this.service = service;
        this.userActivityService = userActivityService;
        this.activityTypeService = activityTypeService;
        this.userService = userService;
    }

    @GetMapping("/findAll")
    public CommonResult<?> findAll() {
        return service.selectAll();
    }

    @GetMapping("/selectAllByPage/{current}/{size}")
    public CommonResult<?> selectAllByPage(@PathVariable int current, @PathVariable int size) {
        return service.selectAllByPage(current, size);
    }

    @PostMapping("/del/{id}")
    public CommonResult<?> deleteLegacy(@PathVariable int id, HttpServletRequest request) {
        return deleteActivity(id, request);
    }

    @GetMapping("/selectNotBegin")
    public CommonResult<?> selectNotBegin() {
        return service.selectNotBegin();
    }

    @GetMapping("/selectByName/{name}")
    public CommonResult<?> selectByName(@PathVariable String name) {
        if (name.isBlank() || name.length() > 100) {
            return CommonResult.validateFailed("活动名称无效");
        }
        return service.selectByName(name.trim());
    }

    @GetMapping("/selectByNameByPage/{name}/{current}/{size}")
    public CommonResult<?> selectByNameByPage(
            @PathVariable String name,
            @PathVariable int current,
            @PathVariable int size) {
        if (name.isBlank() || name.length() > 100) {
            return CommonResult.validateFailed("活动名称无效");
        }
        return service.selectByNameByPage(name.trim(), current, size);
    }

    @GetMapping("/selectByState/{state}")
    public CommonResult<?> selectByState(@PathVariable int state) {
        return state >= 1 && state <= 3
                ? service.selectByState(state)
                : CommonResult.validateFailed("活动状态无效");
    }

    @GetMapping("/selectByType/{activityTypeId}")
    public CommonResult<?> selectByType(@PathVariable int activityTypeId) {
        return activityTypeId > 0
                ? service.selectByType(activityTypeId)
                : CommonResult.validateFailed("活动类型无效");
    }

    @GetMapping("/selectById/{id}")
    public CommonResult<?> selectById(@PathVariable int id) {
        return id > 0 ? service.selectById(id) : CommonResult.validateFailed("活动编号无效");
    }

    @PutMapping("/insert")
    public CommonResult<?> insert(@RequestBody Activity activity, HttpServletRequest request) {
        if (!isActivityManager(request)) {
            return CommonResult.forbidden();
        }
        String error = validateActivity(activity);
        if (error != null) {
            return CommonResult.validateFailed(error);
        }
        activity.setId(null);
        activity.setSignNum(0);
        activity.setState(deriveState(activity));
        return service.insert(activity);
    }

    @PostMapping("/update")
    public CommonResult<?> update(@RequestBody Activity activity, HttpServletRequest request) {
        if (!isActivityManager(request)) {
            return CommonResult.forbidden();
        }
        if (activity.getId() == null || activity.getId() <= 0) {
            return CommonResult.validateFailed("活动编号无效");
        }
        Activity existing = service.getById(activity.getId());
        if (existing == null) {
            return CommonResult.notFound("活动不存在");
        }
        String error = validateActivity(activity);
        if (error != null) {
            return CommonResult.validateFailed(error);
        }
        activity.setSignNum(existing.getSignNum());
        activity.setState(deriveState(activity));
        return service.update(activity);
    }

    @PostMapping("/delete/{id}")
    public CommonResult<?> delete(@PathVariable int id, HttpServletRequest request) {
        return deleteActivity(id, request);
    }

    private CommonResult<?> deleteActivity(int id, HttpServletRequest request) {
        if (!isActivityManager(request)) {
            return CommonResult.forbidden();
        }
        Activity existing = service.getById(id);
        if (existing == null) {
            return CommonResult.notFound("活动不存在");
        }
        long registrations = userActivityService.count(
                new LambdaQueryWrapper<UserActivity>().eq(UserActivity::getaId, id));
        if (registrations > 0) {
            return CommonResult.validateFailed("活动已有报名记录，不能删除");
        }
        return service.removeById(id)
                ? CommonResult.success(true)
                : CommonResult.failed("删除活动失败");
    }

    private String validateActivity(Activity activity) {
        if (activity == null) {
            return "活动信息不能为空";
        }
        if (activity.getActivityName() == null || activity.getActivityName().isBlank()
                || activity.getActivityName().trim().length() > 100) {
            return "活动名称不能为空且不能超过100字";
        }
        if (activity.getActivityTypeId() == null || activity.getActivityTypeId() <= 0) {
            return "请选择活动类型";
        }
        ActivityType activityType = activityTypeService.getById(activity.getActivityTypeId());
        if (activityType == null) {
            return "活动类型不存在";
        }
        if (activity.getActivityDate() == null || activity.getStartTime() == null || activity.getEndTime() == null) {
            return "请填写完整的活动日期和时间";
        }
        if (!activity.getEndTime().isAfter(activity.getStartTime())) {
            return "结束时间必须晚于开始时间";
        }
        if (activity.getActivityAddress() == null || activity.getActivityAddress().isBlank()
                || activity.getActivityAddress().length() > 255) {
            return "活动地点不能为空且不能超过255字";
        }
        if (activity.getdId() == null || activity.getdId() <= 0) {
            return "请选择活动负责人";
        }
        User director = userService.getById(activity.getdId());
        if (director == null || director.getRoleId() == null
                || (director.getRoleId() != 2 && director.getRoleId() != 3)) {
            return "活动负责人不存在或角色不符";
        }
        if (activity.getActivityDetail() != null && activity.getActivityDetail().length() > 255) {
            return "活动内容不能超过255字";
        }
        if (activity.getImage() != null && activity.getImage().length() > 600) {
            return "活动图片地址不能超过600字";
        }
        if (activity.getLimitNum() == null || activity.getLimitNum() <= 0 || activity.getLimitNum() > 10_000) {
            return "活动人数上限必须在1到10000之间";
        }
        if (activity.getActivityPoint() != null
                && (activity.getActivityPoint() < 0 || activity.getActivityPoint() > 10_000)) {
            return "活动积分必须在0到10000之间";
        }
        activity.setActivityName(activity.getActivityName().trim());
        activity.setActivityAddress(activity.getActivityAddress().trim());
        return null;
    }

    private int deriveState(Activity activity) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = LocalDateTime.of(activity.getActivityDate(), activity.getStartTime());
        LocalDateTime end = LocalDateTime.of(activity.getActivityDate(), activity.getEndTime());
        if (!end.isAfter(now)) {
            return 3;
        }
        return start.isAfter(now) ? 1 : 2;
    }

    private boolean isActivityManager(HttpServletRequest request) {
        Object roleValue = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        return roleValue instanceof Integer roleId && (roleId == 1 || roleId == 2);
    }
}
