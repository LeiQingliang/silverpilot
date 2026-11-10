package com.cecsmsserve.service;

import com.cecsmsserve.entity.User;
import com.cecsmsserve.mapper.UserMapper;
import com.cecsmsserve.service.impl.UserServiceImpl;
import com.cecsmsserve.util.JWTUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRegistrationSecurityTests {
    @Test
    void publicRegistrationCannotSetIdentityStaffFieldsPointsOrToken() {
        UserMapper mapper = mock(UserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        UserServiceImpl service = new UserServiceImpl(mapper, mock(JWTUtil.class), encoder);
        User submitted = new User();
        submitted.setUsername("visitor_test");
        submitted.setPassword("registration-test-only");
        submitted.setName("Synthetic registration test");
        submitted.setTelephone("13900000000");
        submitted.setIdNum("110101199001010010");
        submitted.setId(1);
        submitted.setRoleId(1);
        submitted.setDepartment("admin");
        submitted.setPoint(1000000);
        submitted.setToken("client-supplied-token");
        when(encoder.encode("registration-test-only")).thenReturn("encoded-test-password");
        when(mapper.insert(any(User.class))).thenAnswer(invocation -> {
            User stored = invocation.getArgument(0);
            assertNull(stored.getId());
            assertEquals(4, stored.getRoleId());
            assertNull(stored.getDepartment());
            assertEquals(0, stored.getPoint());
            assertNull(stored.getToken());
            assertEquals("encoded-test-password", stored.getPassword());
            stored.setId(42);
            return 1;
        });

        var result = service.register(submitted);

        assertEquals(200, result.getCode());
        assertEquals(42, result.getResult().getId());
        assertNull(result.getResult().getToken());
        verify(mapper).insert(submitted);
    }
}
