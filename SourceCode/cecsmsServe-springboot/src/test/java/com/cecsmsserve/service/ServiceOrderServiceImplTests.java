package com.cecsmsserve.service;

import com.cecsmsserve.mapper.ServiceOrderMapper;
import com.cecsmsserve.entity.ServiceOrder;
import com.cecsmsserve.service.impl.ServiceOrderServiceImpl;
import com.cecsmsserve.util.result.CommonResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ServiceOrderServiceImplTests {

    @Test
    void emptyRatingStatisticsAreAValidSuccessfulResult() {
        ServiceOrderMapper mapper = mock(ServiceOrderMapper.class);
        when(mapper.countRate()).thenReturn(List.of());

        CommonResult<?> result = new ServiceOrderServiceImpl(mapper).countRate();

        assertEquals(200, result.getCode());
        assertNotNull(result.getResult());
        assertTrue(((List<?>) result.getResult()).isEmpty());
    }

    @Test
    void ownerCanAtomicallyCancelPendingServiceOrder() {
        ServiceOrderMapper mapper = mock(ServiceOrderMapper.class);
        ServiceOrder order = new ServiceOrder();
        order.setId(8);
        order.setuId(17);
        order.setOrderState(0);
        when(mapper.selectById(8)).thenReturn(order);
        when(mapper.cancelPendingByUser(8, 17)).thenReturn(1);

        CommonResult<Void> result = new ServiceOrderServiceImpl(mapper).cancelByUser(8, 17);

        assertEquals(200, result.getCode());
        verify(mapper).cancelPendingByUser(8, 17);
    }
}
