package com.cecsmsserve.service;

import com.cecsmsserve.entity.Activity;
import com.cecsmsserve.mapper.ActivityMapper;
import com.cecsmsserve.mapper.UserActivityMapper;
import com.cecsmsserve.mapper.UserMapper;
import com.cecsmsserve.service.impl.ActivityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityServiceImplTests {

    private ActivityMapper activityMapper;
    private UserActivityMapper registrationMapper;
    private UserMapper userMapper;
    private ActivityServiceImpl service;

    @BeforeEach
    void setUp() {
        activityMapper = mock(ActivityMapper.class);
        registrationMapper = mock(UserActivityMapper.class);
        userMapper = mock(UserMapper.class);
        service = new ActivityServiceImpl(activityMapper, registrationMapper, userMapper);
    }

    @Test
    void onlyTheSchedulerThatAtomicallyFinishesAnActivityAwardsPoints() {
        Activity activity = finishedActivity();
        when(activityMapper.selectList(any())).thenReturn(List.of(activity));
        when(activityMapper.markFinishedIfDue(7)).thenReturn(1);
        when(registrationMapper.selectUserByaId(7)).thenReturn(List.of(8, 9));
        when(userMapper.addPoints(8, 5)).thenReturn(1);
        when(userMapper.addPoints(9, 5)).thenReturn(1);

        service.updateActivityStatus();

        verify(userMapper).addPoints(8, 5);
        verify(userMapper).addPoints(9, 5);
    }

    @Test
    void aCompetingSchedulerCannotAwardPointsTwice() {
        Activity activity = finishedActivity();
        when(activityMapper.selectList(any())).thenReturn(List.of(activity));
        when(activityMapper.markFinishedIfDue(7)).thenReturn(0);

        service.updateActivityStatus();

        verify(registrationMapper, never()).selectUserByaId(7);
        verify(userMapper, never()).addPoints(anyInt(), anyInt());
    }

    private Activity finishedActivity() {
        Activity activity = new Activity();
        activity.setId(7);
        activity.setState(2);
        activity.setActivityDate(LocalDate.now().minusDays(1));
        activity.setStartTime(LocalTime.of(9, 0));
        activity.setEndTime(LocalTime.of(10, 0));
        activity.setActivityPoint(5);
        return activity;
    }
}
