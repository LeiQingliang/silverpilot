package com.cecsmsserve.controller;

import com.cecsmsserve.entity.Activity;
import com.cecsmsserve.entity.ActivityType;
import com.cecsmsserve.entity.User;
import com.cecsmsserve.service.IActivityService;
import com.cecsmsserve.service.IActivityTypeService;
import com.cecsmsserve.service.IUserActivityService;
import com.cecsmsserve.service.IUserService;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.result.CommonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityControllerTests {

    private IActivityService activityService;
    private IActivityTypeService activityTypeService;
    private IUserService userService;
    private ActivityController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        activityService = mock(IActivityService.class);
        IUserActivityService registrationService = mock(IUserActivityService.class);
        activityTypeService = mock(IActivityTypeService.class);
        userService = mock(IUserService.class);
        controller = new ActivityController(
                activityService, registrationService, activityTypeService, userService);
        request = new MockHttpServletRequest();
        request.setAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE, 1);
    }

    @Test
    void rejectsActivityDetailBeyondTheDatabaseColumn() {
        stubReferences();
        Activity activity = validActivity();
        activity.setActivityDetail("x".repeat(256));

        CommonResult<?> result = controller.insert(activity, request);

        assertEquals(400, result.getCode());
        verify(activityService, never()).insert(any(Activity.class));
    }

    @Test
    void rejectsImagePathBeyondTheDatabaseColumn() {
        stubReferences();
        Activity activity = validActivity();
        activity.setImage("x".repeat(601));

        CommonResult<?> result = controller.insert(activity, request);

        assertEquals(400, result.getCode());
        verify(activityService, never()).insert(any(Activity.class));
    }

    @Test
    void rejectsMissingActivityTypeBeforeWriting() {
        Activity activity = validActivity();
        when(activityTypeService.getById(1)).thenReturn(null);

        CommonResult<?> result = controller.insert(activity, request);

        assertEquals(400, result.getCode());
        verify(activityService, never()).insert(any(Activity.class));
    }

    @Test
    void rejectsAResponsibleUserWithoutAStaffRole() {
        Activity activity = validActivity();
        ActivityType type = new ActivityType();
        type.setId(1);
        User ordinaryUser = new User();
        ordinaryUser.setId(2);
        ordinaryUser.setRoleId(4);
        when(activityTypeService.getById(1)).thenReturn(type);
        when(userService.getById(2)).thenReturn(ordinaryUser);

        CommonResult<?> result = controller.insert(activity, request);

        assertEquals(400, result.getCode());
        verify(activityService, never()).insert(any(Activity.class));
    }

    @Test
    void validReferencesArePersistedWithNormalizedText() {
        stubReferences();
        Activity activity = validActivity();
        activity.setActivityName("  健康测试  ");
        activity.setActivityAddress("  社区活动室  ");
        when(activityService.insert(any(Activity.class)))
                .thenAnswer(invocation -> CommonResult.success(invocation.getArgument(0)));

        CommonResult<?> result = controller.insert(activity, request);

        assertEquals(200, result.getCode());
        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityService).insert(captor.capture());
        assertEquals("健康测试", captor.getValue().getActivityName());
        assertEquals("社区活动室", captor.getValue().getActivityAddress());
    }

    private void stubReferences() {
        ActivityType type = new ActivityType();
        type.setId(1);
        type.setState(1);
        User director = new User();
        director.setId(2);
        director.setRoleId(2);
        when(activityTypeService.getById(1)).thenReturn(type);
        when(userService.getById(2)).thenReturn(director);
    }

    private Activity validActivity() {
        Activity activity = new Activity();
        activity.setActivityName("健康测试");
        activity.setActivityTypeId(1);
        activity.setActivityDate(LocalDate.now().plusDays(7));
        activity.setStartTime(LocalTime.of(9, 0));
        activity.setEndTime(LocalTime.of(10, 0));
        activity.setActivityAddress("社区活动室");
        activity.setdId(2);
        activity.setActivityDetail("边界合法");
        activity.setActivityPoint(1);
        activity.setLimitNum(10);
        return activity;
    }
}
