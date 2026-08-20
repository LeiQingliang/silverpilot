package com.cecsmsserve.service;

import com.cecsmsserve.entity.RecipeOrder;
import com.cecsmsserve.entity.User;
import com.cecsmsserve.mapper.RecipeOrderMapper;
import com.cecsmsserve.service.impl.RecipeOrderServiceImpl;
import com.cecsmsserve.util.result.CommonResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeOrderServiceImplTests {

    @Test
    void ordinaryUserCannotCompleteAnOrder() {
        RecipeOrderMapper mapper = mock(RecipeOrderMapper.class);
        IUserService userService = mock(IUserService.class);
        RecipeOrder pending = pendingOrder(7, 17);
        User user = new User();
        user.setId(17);
        user.setRoleId(4);
        when(mapper.selectById(7)).thenReturn(pending);
        when(userService.getById(17)).thenReturn(user);

        CommonResult<Void> result = new RecipeOrderServiceImpl(mapper, userService)
                .updateOrderStatus(7, 1, 17);

        assertEquals(403, result.getCode());
        verify(mapper, never()).updateOrderStatus(7, 1);
    }

    @Test
    void ownerCanAtomicallyCancelAPendingOrder() {
        RecipeOrderMapper mapper = mock(RecipeOrderMapper.class);
        IUserService userService = mock(IUserService.class);
        when(mapper.selectById(7)).thenReturn(pendingOrder(7, 17));
        when(mapper.updateOrderStatus(7, 2)).thenReturn(1);

        CommonResult<Void> result = new RecipeOrderServiceImpl(mapper, userService)
                .cancelOrder(7, 17);

        assertEquals(200, result.getCode());
        verify(mapper).updateOrderStatus(7, 2);
    }

    private RecipeOrder pendingOrder(int id, int userId) {
        RecipeOrder order = new RecipeOrder();
        order.setId(id);
        order.setUserId(userId);
        order.setStatus(0);
        return order;
    }
}
