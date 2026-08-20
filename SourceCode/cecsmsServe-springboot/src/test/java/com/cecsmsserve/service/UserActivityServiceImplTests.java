package com.cecsmsserve.service;

import com.cecsmsserve.entity.UserActivity;
import com.cecsmsserve.mapper.ActivityMapper;
import com.cecsmsserve.mapper.UserActivityMapper;
import com.cecsmsserve.mapper.UserMapper;
import com.cecsmsserve.service.impl.UserActivityServiceImpl;
import com.cecsmsserve.util.result.CommonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserActivityServiceImplTests {

    private UserActivityMapper registrationMapper;
    private ActivityMapper activityMapper;
    private UserActivityServiceImpl service;

    @BeforeEach
    void setUp() {
        registrationMapper = mock(UserActivityMapper.class);
        activityMapper = mock(ActivityMapper.class);
        service = new UserActivityServiceImpl(
                registrationMapper, activityMapper, mock(UserMapper.class));
    }

    @Test
    void cancellationChangesTheRegistrationAndCountExactlyOnce() {
        UserActivity registration = registration();
        when(registrationMapper.cancelConfirmed(9, 17, 3)).thenReturn(1);
        when(activityMapper.releaseClaimedSlot(3)).thenReturn(1);

        CommonResult<Integer> result = service.cancel(registration);

        assertEquals(200, result.getCode());
        verify(activityMapper).releaseClaimedSlot(3);
    }

    @Test
    void repeatedCancellationCannotDecrementTheCountAgain() {
        UserActivity registration = registration();
        when(registrationMapper.cancelConfirmed(9, 17, 3)).thenReturn(0);

        CommonResult<Integer> result = service.cancel(registration);

        assertEquals(400, result.getCode());
        verify(activityMapper, never()).releaseClaimedSlot(3);
    }

    private UserActivity registration() {
        UserActivity registration = new UserActivity();
        registration.setId(9);
        registration.setuId(17);
        registration.setaId(3);
        registration.setState("已取消报名");
        return registration;
    }
}
