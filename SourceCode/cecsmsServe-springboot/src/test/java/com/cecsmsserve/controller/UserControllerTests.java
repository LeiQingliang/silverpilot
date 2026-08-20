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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
