package com.cecsmsserve.controller;

import com.cecsmsserve.entity.Activity;
import com.cecsmsserve.entity.UserActivity;
import com.cecsmsserve.service.IActivityService;
import com.cecsmsserve.service.IUserActivityService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

@RestController
@RequestMapping("/userActivity")
public class UserActivityController {

    private static final Set<String> ALLOWED_FILTER_STATES = Set.of(
            "报名成功", "报名审核中", "已取消报名", "未开始", "进行中", "已结束");

    private final IUserActivityService service;
    private final IActivityService activityService;

    public UserActivityController(IUserActivityService service, IActivityService activityService) {
        this.service = service;
        this.activityService = activityService;
    }

    @GetMapping("/selectAll")
    public CommonResult<?> selectAll(HttpServletRequest request) {
        return isActivityManager(request) ? service.selectAll() : CommonResult.forbidden();
    }

    @GetMapping("/selectAllByPage/{current}/{size}")
    public CommonResult<?> selectAllByPage(
            @PathVariable int current,
            @PathVariable int size,
            HttpServletRequest request) {
        return isActivityManager(request)
                ? service.selectAllByPage(current, size)
                : CommonResult.forbidden();
    }

    @GetMapping("/selectAllByuId/{uId}")
    public CommonResult<?> selectAllByUserId(@PathVariable int uId, HttpServletRequest request) {
        return mayReadUser(request, uId)
                ? service.selectAllByuId(uId)
                : CommonResult.forbidden();
    }

    @GetMapping("/selectByUIdBymyState/{uId}/{state}")
    public CommonResult<?> selectByUserIdAndRegistrationState(
            @PathVariable int uId,
            @PathVariable String state,
            HttpServletRequest request) {
        if (!mayReadUser(request, uId)) {
            return CommonResult.forbidden();
        }
        if (!ALLOWED_FILTER_STATES.contains(state)) {
            return CommonResult.validateFailed("报名状态无效");
        }
        return service.selectByUIdBymyState(uId, state);
    }

    @GetMapping("/selectByUIdByState/{uId}/{state}")
    public CommonResult<?> selectByUserIdAndActivityState(
            @PathVariable int uId,
            @PathVariable String state,
            HttpServletRequest request) {
        if (!mayReadUser(request, uId)) {
            return CommonResult.forbidden();
        }
        if (!ALLOWED_FILTER_STATES.contains(state)) {
            return CommonResult.validateFailed("活动状态无效");
        }
        return service.selectByUIdByState(uId, state);
    }

    @GetMapping("/selectByuIdByaId/{uId}/{aId}")
    public CommonResult<?> selectByUserIdAndActivityId(
            @PathVariable int uId,
            @PathVariable int aId,
            HttpServletRequest request) {
        return mayReadUser(request, uId)
                ? service.selectByuIdByaId(uId, aId)
                : CommonResult.forbidden();
    }

    @GetMapping("/selectUserByaId/{aId}")
    public CommonResult<?> selectUsersByActivityId(@PathVariable int aId, HttpServletRequest request) {
        return isActivityManager(request)
                ? service.selectUserByaId(aId)
                : CommonResult.forbidden();
    }

    @PutMapping("/insert")
    public CommonResult<?> insert(@RequestBody UserActivity requestBody, HttpServletRequest request) {
        Integer userId = currentUserId(request);
        if (userId == null) {
            return CommonResult.unauthorized();
        }
        if (requestBody.getaId() == null || requestBody.getaId() <= 0) {
            return CommonResult.validateFailed("活动编号无效");
        }
        UserActivity registration = new UserActivity();
        registration.setuId(userId);
        registration.setaId(requestBody.getaId());
        registration.setEnterDate(LocalDate.now());
        registration.setEnterTime(LocalTime.now().withNano(0));
        return service.insert(registration);
    }

    @PostMapping("/update")
    public CommonResult<?> update(@RequestBody UserActivity requestBody, HttpServletRequest request) {
        if (requestBody.getId() == null || requestBody.getId() <= 0) {
            return CommonResult.validateFailed("报名记录编号无效");
        }
        UserActivity existing = service.getById(requestBody.getId());
        if (existing == null) {
            return CommonResult.notFound("报名记录不存在");
        }
        Integer userId = currentUserId(request);
        if (userId == null || !userId.equals(existing.getuId())) {
            return CommonResult.forbidden("只能取消自己的报名");
        }
        if (!"报名成功".equals(existing.getState()) || !"已取消报名".equals(requestBody.getState())) {
            return CommonResult.validateFailed("当前报名状态不允许取消");
        }
        Activity activity = activityService.getById(existing.getaId());
        if (activity == null) {
            return CommonResult.notFound("活动不存在");
        }
        if (!isBeforeActivityStart(activity)) {
            return CommonResult.validateFailed("活动已开始，不能取消报名");
        }

        UserActivity update = new UserActivity();
        update.setId(existing.getId());
        update.setuId(userId);
        update.setaId(existing.getaId());
        update.setState("已取消报名");
        return service.cancel(update);
    }

    private boolean isBeforeActivityStart(Activity activity) {
        if (activity.getActivityDate() == null) {
            return false;
        }
        LocalTime start = activity.getStartTime() == null ? LocalTime.MIN : activity.getStartTime();
        return LocalDateTime.of(activity.getActivityDate(), start).isAfter(LocalDateTime.now());
    }

    private boolean mayReadUser(HttpServletRequest request, int requestedUserId) {
        Integer current = currentUserId(request);
        return current != null && (current == requestedUserId || isActivityManager(request));
    }

    private boolean isActivityManager(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        return value instanceof Integer roleId && (roleId == 1 || roleId == 2);
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ID_ATTRIBUTE);
        return value instanceof Integer id ? id : null;
    }
}
