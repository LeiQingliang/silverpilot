package com.cecsmsserve.service;

import com.cecsmsserve.entity.Activity;
import com.cecsmsserve.util.result.CommonResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolExecutorTests {

    @Test
    void writeToolIsPreparedButNotExecutedBeforeConfirmation() {
        IActivityService activityService = mock(IActivityService.class);
        IUserActivityService userActivityService = mock(IUserActivityService.class);
        Activity activity = new Activity();
        activity.setId(9);
        activity.setActivityName("健康讲座");
        activity.setActivityDate(LocalDate.now().plusDays(1));
        activity.setStartTime(LocalTime.of(9, 0));
        activity.setState(1);
        activity.setLimitNum(20);
        activity.setSignNum(3);
        when(activityService.getById(9)).thenReturn(activity);
        when(userActivityService.selectByuIdByaId(17, 9)).thenReturn(CommonResult.success(null));

        ToolExecutor executor = executor(activityService, userActivityService);
        ToolExecutor.ToolPlan plan = executor.prepare("join_activity", Map.of("activityId", 9), 17);

        assertTrue(plan.requiresConfirmation());
        assertEquals("join_activity", plan.name());
        assertTrue(plan.summary().contains("健康讲座"));
        verify(userActivityService, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void readToolNeverRequiresConfirmation() {
        ToolExecutor.ToolPlan plan = executor(mock(IActivityService.class), mock(IUserActivityService.class))
                .prepare("my_service_orders", Map.of(), 17);

        assertFalse(plan.requiresConfirmation());
    }

    @Test
    void unknownToolIsRejected() {
        ToolExecutor executor = executor(mock(IActivityService.class), mock(IUserActivityService.class));
        assertThrows(IllegalArgumentException.class,
                () -> executor.prepare("delete_everything", Map.of(), 17));
    }

    private ToolExecutor executor(
            IActivityService activityService, IUserActivityService userActivityService) {
        return new ToolExecutor(
                activityService,
                userActivityService,
                mock(IServiceTypeService.class),
                mock(IServiceOrderService.class),
                mock(IReportService.class),
                mock(IRecipeService.class),
                mock(IRecipeOrderService.class));
    }
}
