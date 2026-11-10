package com.cecsmsserve.util;

import com.cecsmsserve.controller.ChatController;
import com.cecsmsserve.controller.UserController;
import com.cecsmsserve.entity.User;
import com.cecsmsserve.service.IUserService;
import com.cecsmsserve.util.result.CommonResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.HandlerMethod;

import java.net.URI;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DemoAccessPolicyTests {
    private final DemoAccessPolicy policy = new DemoAccessPolicy(true, "1,17");

    @Test
    void bindsOnlyConfiguredIdsAndRejectsEmptyEnabledConfiguration() {
        assertTrue(policy.isReadOnly(1));
        assertTrue(policy.isReadOnly(17));
        assertFalse(policy.isReadOnly(19));
        assertFalse(policy.isReadOnly(null));
        assertFalse(new DemoAccessPolicy(false, "1,17").isReadOnly(1));
        assertThrows(IllegalArgumentException.class, () -> new DemoAccessPolicy(true, ""));
        assertThrows(IllegalArgumentException.class, () -> new DemoAccessPolicy(true, "0"));
    }

    @Test
    void onlyResolvedReadHandlersAndTwoChatComputationsAreAllowed() {
        UserController users = mock(UserController.class);
        ChatController chat = mock(ChatController.class);
        assertTrue(policy.permits("GET", handler(users, "selectById")));
        assertTrue(policy.permits("HEAD", handler(users, "selectById")));
        assertFalse(policy.permits("GET", handler(users, "update")));
        assertFalse(policy.permits("POST", handler(users, "update")));
        assertFalse(policy.permits("PUT", handler(users, "insert")));
        assertTrue(policy.permits("POST", handler(chat, "chat")));
        assertTrue(policy.permits("POST", handler(chat, "carePlan")));
        assertFalse(policy.permits("GET", handler(chat, "chat")));
        assertTrue(policy.permits("GET", handler(chat, "actionHistory")));
        assertTrue(policy.permits("HEAD", handler(chat, "actionHistory")));
        for (var method : ChatController.class.getDeclaredMethods()) {
            if (method.getName().toLowerCase().contains("confirm")) {
                assertFalse(policy.permits("POST", new HandlerMethod(chat, method)));
            }
        }
        assertFalse(policy.permits("POST", new Object()));
    }

    @Test
    void mvcMatrixAndEncodedPathsCannotReachPasswordUpdates() throws Exception {
        IUserService service = mock(IUserService.class);
        JWTUtil jwt = mock(JWTUtil.class);
        User demo = user(1, 1, "renamed_account");
        when(jwt.getUserId("demo-session")).thenReturn(1);
        when(service.getById(1)).thenReturn(demo);
        UserController controller = new UserController(service,
                mock(AuthenticationRateLimiter.class), mock(CaptchaService.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new JWTInterceptor(service, jwt, policy)).build();
        for (String path : new String[]{"/user/update", "/user;v=1/update;v=2", "/user/%75pdate"}) {
            mvc.perform(post(URI.create(path)).header("Authorization", "Bearer demo-session")
                            .header("X-HTTP-Method-Override", "GET")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"id\":1,\"password\":\"changed-password\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        }
        mvc.perform(put("/user/insert").header("Authorization", "Bearer demo-session")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/user/update").header("Authorization", "Bearer demo-session"))
                .andExpect(status().isMethodNotAllowed());
        verify(service, never()).update(any(User.class));
        verify(service, never()).insert(any());
    }

    @Test
    void demoRoleChangesDoNotRemoveRestrictionAndPrivateOwnerStillWorks() throws Exception {
        IUserService service = mock(IUserService.class);
        JWTUtil jwt = mock(JWTUtil.class);
        when(jwt.getUserId("viewer-session")).thenReturn(17);
        when(jwt.getUserId("owner-session")).thenReturn(19);
        when(service.getById(17)).thenReturn(user(17, 1, "changed_name"));
        when(service.getById(19)).thenReturn(user(19, 1, "private_owner"));
        when(service.update(any(User.class))).thenReturn(new CommonResult<>());
        UserController controller = new UserController(service,
                mock(AuthenticationRateLimiter.class), mock(CaptchaService.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new JWTInterceptor(service, jwt, policy)).build();
        mvc.perform(post("/user/update").header("Authorization", "Bearer viewer-session")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":19}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/user/update").header("Authorization", "Bearer owner-session")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":19}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service, times(1)).update(any(User.class));
    }

    @Test
    void regularUsersRetainExistingOwnershipChecksAndSharedLoginStillAuthenticates() throws Exception {
        IUserService service = mock(IUserService.class);
        JWTUtil jwt = mock(JWTUtil.class);
        when(jwt.getUserId("regular-session")).thenReturn(20);
        when(service.getById(20)).thenReturn(user(20, 4, "regular_user"));
        UserController controller = new UserController(service,
                mock(AuthenticationRateLimiter.class), mock(CaptchaService.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new JWTInterceptor(service, jwt, policy)).build();
        mvc.perform(post("/user/update").header("Authorization", "Bearer regular-session")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":19}"))
                .andExpect(jsonPath("$.code").value(403));
        verify(service, never()).update(any(User.class));

        when(jwt.getUserId("demo-session")).thenReturn(1);
        when(service.getById(1)).thenReturn(user(1, 1, "admin"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/selectById/1");
        request.addHeader("Authorization", "Bearer demo-session");
        assertTrue(new JWTInterceptor(service, jwt, policy).preHandle(
                request, new MockHttpServletResponse(), handler(controller, "selectById")));
        assertEquals(1, request.getAttribute(JWTInterceptor.USER_ID_ATTRIBUTE));
    }

    private HandlerMethod handler(Object controller, String name) {
        Class<?> type = controller instanceof ChatController ? ChatController.class : UserController.class;
        return new HandlerMethod(controller, Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name)).findFirst().orElseThrow());
    }

    private User user(int id, int roleId, String username) {
        User user = new User();
        user.setId(id);
        user.setRoleId(roleId);
        user.setUsername(username);
        return user;
    }
}
