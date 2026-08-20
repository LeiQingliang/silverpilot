package com.cecsmsserve.controller;

import com.cecsmsserve.entity.ServiceOrder;
import com.cecsmsserve.entity.ServiceType;
import com.cecsmsserve.service.IServiceOrderService;
import com.cecsmsserve.service.IServiceTypeService;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.result.CommonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceOrderControllerTests {

    private IServiceOrderService orderService;
    private IServiceTypeService typeService;
    private ServiceOrderController controller;

    @BeforeEach
    void setUp() {
        orderService = mock(IServiceOrderService.class);
        typeService = mock(IServiceTypeService.class);
        controller = new ServiceOrderController(orderService, typeService);
    }

    @Test
    void insertUsesAuthenticatedUserAndServerTimestamp() {
        ServiceType parent = serviceType(1, null);
        ServiceType child = serviceType(6, 1);
        when(typeService.getById(1)).thenReturn(parent);
        when(typeService.getById(6)).thenReturn(child);
        when(orderService.insert(any())).thenReturn(CommonResult.success(1));

        ServiceOrder input = new ServiceOrder();
        input.setuId(999);
        input.setTypeBId(1);
        input.setTypeSId(6);
        input.setReserveDate(LocalDate.now().plusDays(1));
        input.setServiceAddress("  家里  ");
        input.setOrderDetail("  需要协助  ");

        CommonResult<?> result = controller.insert(input, request(17, 4));

        assertEquals(200, result.getCode());
        ArgumentCaptor<ServiceOrder> captor = ArgumentCaptor.forClass(ServiceOrder.class);
        verify(orderService).insert(captor.capture());
        ServiceOrder saved = captor.getValue();
        assertEquals(17, saved.getuId());
        assertEquals("家里", saved.getServiceAddress());
        assertEquals("需要协助", saved.getOrderDetail());
        assertEquals(LocalDate.now(), saved.getOrderDate());
        assertNotNull(saved.getOrderTime());
        assertNull(saved.getmId());
    }

    @Test
    void ordinaryUserCannotUpdateAnotherUsersOrder() {
        ServiceOrder existing = new ServiceOrder();
        existing.setId(22);
        existing.setuId(8);
        existing.setOrderState(0);
        when(orderService.getById(22)).thenReturn(existing);

        ServiceOrder input = new ServiceOrder();
        input.setId(22);
        input.setOrderState(1);

        CommonResult<?> result = controller.update(input, request(17, 4));

        assertEquals(403, result.getCode());
        verify(orderService, never()).transition(any(ServiceOrder.class), anyInt());
        verify(orderService, never()).cancelByUser(any(Integer.class), any(Integer.class));
    }

    @Test
    void workerCannotAcceptHealthOrder() {
        ServiceOrder existing = new ServiceOrder();
        existing.setId(22);
        existing.setuId(17);
        existing.setTypeBId(4);
        existing.setOrderState(0);
        when(orderService.getById(22)).thenReturn(existing);

        ServiceOrder input = new ServiceOrder();
        input.setId(22);
        input.setOrderState(2);
        input.setName("服务人员");
        input.setTelephone("10000000001");

        CommonResult<?> result = controller.update(input, request(2, 2));

        assertEquals(403, result.getCode());
        verify(orderService, never()).transition(any(ServiceOrder.class), anyInt());
    }

    @Test
    void managerAcceptanceUsesAnAtomicExpectedStateTransition() {
        ServiceOrder existing = new ServiceOrder();
        existing.setId(22);
        existing.setuId(17);
        existing.setTypeBId(1);
        existing.setOrderState(0);
        when(orderService.getById(22)).thenReturn(existing);
        when(orderService.transition(any(ServiceOrder.class), eq(0)))
                .thenReturn(CommonResult.success(1));

        ServiceOrder input = new ServiceOrder();
        input.setId(22);
        input.setOrderState(2);
        input.setName("王社工");
        input.setTelephone("10000000001");

        CommonResult<?> result = controller.update(input, request(2, 2));

        assertEquals(200, result.getCode());
        verify(orderService).transition(any(ServiceOrder.class), eq(0));
    }

    @Test
    void ownerCancellationUsesTheOwnershipCheckedAtomicUpdate() {
        ServiceOrder existing = new ServiceOrder();
        existing.setId(22);
        existing.setuId(17);
        existing.setOrderState(0);
        when(orderService.getById(22)).thenReturn(existing);
        when(orderService.cancelByUser(22, 17)).thenReturn(CommonResult.success(null));

        ServiceOrder input = new ServiceOrder();
        input.setId(22);
        input.setOrderState(1);

        CommonResult<?> result = controller.update(input, request(17, 4));

        assertEquals(200, result.getCode());
        verify(orderService).cancelByUser(22, 17);
    }

    private ServiceType serviceType(int id, Integer leaderId) {
        ServiceType type = new ServiceType();
        type.setId(id);
        type.setLeaderId(leaderId);
        type.setState(1);
        return type;
    }

    private MockHttpServletRequest request(int userId, int roleId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JWTInterceptor.USER_ID_ATTRIBUTE, userId);
        request.setAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE, roleId);
        return request;
    }
}
