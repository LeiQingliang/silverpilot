package com.cecsmsserve.controller;

import com.cecsmsserve.entity.ActivityType;
import com.cecsmsserve.service.IActivityTypeService;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.result.CommonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ActivityTypeControllerTests {

    private IActivityTypeService service;
    private ActivityTypeController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        service = mock(IActivityTypeService.class);
        controller = new ActivityTypeController(service);
        request = new MockHttpServletRequest();
        request.setAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE, 1);
    }

    @Test
    void detailCannotExceedTheDatabaseColumnLength() {
        ActivityType input = validType();
        input.setDetail("x".repeat(256));

        CommonResult<?> result = controller.insert(input, request);

        assertEquals(400, result.getCode());
        verify(service, never()).insert(any(ActivityType.class));
    }

    @Test
    void purposeCannotExceedTheDatabaseColumnLength() {
        ActivityType input = validType();
        input.setPurpose("x".repeat(256));

        CommonResult<?> result = controller.insert(input, request);

        assertEquals(400, result.getCode());
        verify(service, never()).insert(any(ActivityType.class));
    }

    private ActivityType validType() {
        ActivityType type = new ActivityType();
        type.setType("健康讲座");
        type.setDetail("社区健康讲座");
        type.setPurpose("健康宣教");
        type.setState(1);
        return type;
    }
}
