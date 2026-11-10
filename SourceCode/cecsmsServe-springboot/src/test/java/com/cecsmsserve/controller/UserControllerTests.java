package com.cecsmsserve.controller;

import com.cecsmsserve.entity.User;
import com.cecsmsserve.service.IUserService;
import com.cecsmsserve.util.AuthenticationRateLimiter;
import com.cecsmsserve.util.CaptchaService;
import com.cecsmsserve.util.result.CommonResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTests {

    private IUserService userService;
    private AuthenticationRateLimiter rateLimiter;
    private CaptchaService captchaService;
    private UserController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        userService = mock(IUserService.class);
        rateLimiter = mock(AuthenticationRateLimiter.class);
        captchaService = mock(CaptchaService.class);
        controller = new UserController(userService, rateLimiter, captchaService);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
    }

    @Test
    void issuesNoStoreJpegAndPersistsGeneratedChallenge() throws Exception {
        when(captchaService.isValidKey("challenge-1")).thenReturn(true);
        when(rateLimiter.acquireCaptcha("203.0.113.7"))
                .thenReturn(new AuthenticationRateLimiter.Decision(true, 0));
        when(captchaService.store(eq("challenge-1"), anyString())).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.getVerificationCode("challenge-1", request, response);

        assertEquals(200, response.getStatus());
        assertEquals("image/jpeg", response.getContentType());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("no-cache", response.getHeader("Pragma"));
        byte[] jpeg = response.getContentAsByteArray();
        assertTrue(jpeg.length > 100);
        assertEquals(0xFF, Byte.toUnsignedInt(jpeg[0]));
        assertEquals(0xD8, Byte.toUnsignedInt(jpeg[1]));
        verify(captchaService).store(eq("challenge-1"), matches("[2-9A-HJ-NP-Z]{4}"));
    }

    @Test
    void successfulLoginConsumesCaptchaNormalizesIdentityAndResetsLimiter() {
        User user = new User();
        user.setId(1);
        user.setRoleId(1);
        user.setToken("test-token");
        CommonResult<User> serviceResult = CommonResult.success(user);
        when(captchaService.isValidKey("challenge-1")).thenReturn(true);
        when(captchaService.consumeMatches("challenge-1", "aBcD")).thenReturn(true);
        when(rateLimiter.acquireLogin("203.0.113.7", "admin"))
                .thenReturn(new AuthenticationRateLimiter.Decision(true, 0));
        when(userService.login("admin", "password")).thenReturn(serviceResult);

        CommonResult<?> result = controller.login(
                "challenge-1", "  admin  ", "password", "aBcD", request);

        assertSame(serviceResult, result);
        assertEquals(200, result.getCode());
        verify(userService).login("admin", "password");
        verify(rateLimiter).resetLogin("203.0.113.7", "admin");
    }

    @Test
    void wrongCaptchaIsRejectedBeforeCredentialLookup() {
        when(captchaService.isValidKey("challenge-1")).thenReturn(true);
        when(captchaService.consumeMatches("challenge-1", "wrong")).thenReturn(false);

        CommonResult<?> result = controller.login(
                "challenge-1", "admin", "password", "wrong", request);

        assertEquals(108, result.getCode());
        verify(userService, never()).login(anyString(), anyString());
        verify(rateLimiter, never()).acquireLogin(anyString(), anyString());
    }

    @Test
    void rateLimitedLoginDoesNotQueryCredentials() {
        when(captchaService.isValidKey("challenge-1")).thenReturn(true);
        when(captchaService.consumeMatches("challenge-1", "ABCD")).thenReturn(true);
        when(rateLimiter.acquireLogin("203.0.113.7", "admin"))
                .thenReturn(new AuthenticationRateLimiter.Decision(false, 30));

        CommonResult<?> result = controller.login(
                "challenge-1", "admin", "password", "ABCD", request);

        assertEquals(429, result.getCode());
        verify(userService, never()).login(anyString(), anyString());
    }

    @Test
    void malformedCaptchaKeyIsRejectedWithoutStorageLookup() {
        when(captchaService.isValidKey("../unsafe")).thenReturn(false);

        CommonResult<?> result = controller.login(
                "../unsafe", "admin", "password", "ABCD", request);

        assertEquals(400, result.getCode());
        verify(captchaService, never()).consumeMatches(anyString(), anyString());
    }

    @Test
    void registrationRequiresCaptchaAndDoesNotAcceptLegacyUnguardedPayload() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        CommonResult<?> result = controller.register(registration(null, null), request, response);
        assertEquals(400, result.getCode());
        verify(userService, never()).register(any());
        verify(rateLimiter, never()).acquireRegistration(anyString());
    }

    @Test
    void registrationRejectsWrongCaptchaWithoutCreatingAccount() {
        allowRegistrationAttempt();
        when(captchaService.consumeForRegistration("registration-test", "ABCD"))
                .thenReturn(CaptchaService.Verification.MISMATCH);
        CommonResult<?> result = controller.register(
                registration("registration-test", "ABCD"), request, new MockHttpServletResponse());
        assertEquals(108, result.getCode());
        verify(userService, never()).register(any());
        verify(rateLimiter, never()).acquireRegistration(anyString());
    }

    @Test
    void registrationLimitReturns429AndRetryAfterWithoutCreatingAccount() {
        when(captchaService.isValidKey("registration-test")).thenReturn(true);
        when(captchaService.consumeForRegistration("registration-test", "ABCD"))
                .thenReturn(CaptchaService.Verification.MATCH);
        when(rateLimiter.acquireRegistration("203.0.113.7"))
                .thenReturn(new AuthenticationRateLimiter.RegistrationDecision(false, true, 120));
        MockHttpServletResponse response = new MockHttpServletResponse();
        CommonResult<?> result = controller.register(registration("registration-test", "ABCD"), request, response);
        assertEquals(429, result.getCode());
        assertEquals(429, response.getStatus());
        assertEquals("120", response.getHeader("Retry-After"));
        verify(userService, never()).register(any());
    }

    @Test
    void unavailableRegistrationBudgetOrCaptchaFailsClosed() {
        when(captchaService.isValidKey("registration-test")).thenReturn(true);
        when(captchaService.consumeForRegistration("registration-test", "ABCD"))
                .thenReturn(CaptchaService.Verification.MATCH);
        when(rateLimiter.acquireRegistration("203.0.113.7"))
                .thenReturn(new AuthenticationRateLimiter.RegistrationDecision(false, false, 60));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertEquals(503, controller.register(registration("registration-test", "ABCD"), request, response).getCode());
        assertEquals(503, response.getStatus());

        allowRegistrationAttempt();
        when(captchaService.consumeForRegistration("registration-test", "ABCD"))
                .thenReturn(CaptchaService.Verification.UNAVAILABLE);
        MockHttpServletResponse captchaResponse = new MockHttpServletResponse();
        assertEquals(503, controller.register(
                registration("registration-test", "ABCD"), request, captchaResponse).getCode());
        assertEquals(503, captchaResponse.getStatus());
        verify(userService, never()).register(any());
    }

    @Test
    void publicRegistrationDtoDropsIdentityRoleAndRewardFields() throws Exception {
        when(captchaService.isValidKey("registration-test")).thenReturn(true);
        when(rateLimiter.acquireRegistration(anyString()))
                .thenReturn(new AuthenticationRateLimiter.RegistrationDecision(true, true, 0));
        when(captchaService.consumeForRegistration("registration-test", "ABCD"))
                .thenReturn(CaptchaService.Verification.MATCH);
        when(userService.register(any())).thenReturn(CommonResult.success(new User()));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(put("/user/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"new_test_user","password":"test-only-password","name":"Test User",
                 "telephone":"test-phone","idNum":"test-identity","sex":"男","address":"测试地址",
                 "captchaId":"registration-test","verificationCode":"ABCD",
                 "id":1,"roleId":1,"role":1,"point":9999,"department":"privileged","token":"untrusted"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        ArgumentCaptor<User> captured = ArgumentCaptor.forClass(User.class);
        verify(userService).register(captured.capture());
        User submitted = captured.getValue();
        assertEquals("new_test_user", submitted.getUsername());
        assertEquals("test-only-password", submitted.getPassword());
        assertNull(submitted.getId());
        assertNull(submitted.getRoleId());
        assertNull(submitted.getPoint());
        assertNull(submitted.getDepartment());
        assertNull(submitted.getToken());
    }

    private void allowRegistrationAttempt() {
        when(captchaService.isValidKey("registration-test")).thenReturn(true);
        when(rateLimiter.acquireRegistration("203.0.113.7"))
                .thenReturn(new AuthenticationRateLimiter.RegistrationDecision(true, true, 0));
    }

    private RegistrationRequest registration(String captchaId, String code) {
        return new RegistrationRequest("new_test_user", "test-only-password", "Test User", "test-phone",
                "test-identity", "男", "测试地址", captchaId, code);
    }
}
