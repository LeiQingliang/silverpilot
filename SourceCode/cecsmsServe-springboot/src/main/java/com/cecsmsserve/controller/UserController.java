package com.cecsmsserve.controller;

import com.cecsmsserve.entity.User;
import com.cecsmsserve.service.IUserService;
import com.cecsmsserve.util.CaptchaService;
import com.cecsmsserve.util.JWTInterceptor;
import com.cecsmsserve.util.AuthenticationRateLimiter;
import com.cecsmsserve.util.VerificationCodeUtil;
import com.cecsmsserve.util.result.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.awt.image.BufferedImage;
import java.io.IOException;
@RestController
@RequestMapping("/user")
public class UserController {

    private final IUserService service;
    private final AuthenticationRateLimiter authenticationRateLimiter;
    private final CaptchaService captchaService;

    public UserController(
            IUserService service,
            AuthenticationRateLimiter authenticationRateLimiter,
            CaptchaService captchaService) {
        this.service = service;
        this.authenticationRateLimiter = authenticationRateLimiter;
        this.captchaService = captchaService;
    }

    @PutMapping("/register")
    public CommonResult<?> register(@RequestBody User user) {
        return service.register(user);
    }

    @GetMapping("/getVerificationCode/{key}")
    public void getVerificationCode(
            @PathVariable String key,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        if (!captchaService.isValidKey(key)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "验证码标识无效");
            return;
        }
        AuthenticationRateLimiter.Decision decision = authenticationRateLimiter.acquireCaptcha(request.getRemoteAddr());
        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.sendError(429, "验证码请求过于频繁，请稍后重试");
            return;
        }
        VerificationCodeUtil code = new VerificationCodeUtil();
        BufferedImage image = code.getImage();
        if (!captchaService.store(key, code.getText())) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "验证码服务暂时不可用，请稍后重试");
            return;
        }
        response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setContentType("image/jpeg");
        VerificationCodeUtil.output(image, response.getOutputStream());
    }

    @PostMapping("/login/{key}")
    public CommonResult<?> login(
            @PathVariable String key,
            @RequestParam String loginName,
            @RequestParam String password,
            @RequestParam String code,
            HttpServletRequest request) {
        if (!captchaService.isValidKey(key)) {
            return CommonResult.validateFailed("验证码标识无效");
        }
        if (!captchaService.consumeMatches(key, code)) {
            return new CommonResult<>(108, "验证码错误或已过期", null);
        }
        String normalizedLogin = loginName == null ? "" : loginName.trim();
        AuthenticationRateLimiter.Decision decision = authenticationRateLimiter.acquireLogin(
                request.getRemoteAddr(), normalizedLogin);
        if (!decision.allowed()) {
            return new CommonResult<>(429, "登录尝试过于频繁，请稍后重试", null);
        }
        CommonResult<?> result = service.login(normalizedLogin, password);
        if (result.getCode() == 200) {
            authenticationRateLimiter.resetLogin(request.getRemoteAddr(), normalizedLogin);
        }
        return result;
    }

    @PutMapping("/insert")
    public CommonResult<?> insert(@RequestBody User user, HttpServletRequest request) {
        return isAdmin(request) ? service.insert(user) : CommonResult.forbidden();
    }

    @PostMapping("/update")
    public CommonResult<?> update(@RequestBody User user, HttpServletRequest request) {
        Integer currentUserId = currentUserId(request);
        if (currentUserId == null) {
            return CommonResult.unauthorized();
        }
        if (user == null || user.getId() == null) {
            return CommonResult.validateFailed("用户编号不能为空");
        }
        if (isAdmin(request)) {
            if (service.getById(user.getId()) == null) {
                return CommonResult.notFound("用户不存在");
            }
            return service.update(user);
        }
        if (!currentUserId.equals(user.getId())) {
            return CommonResult.forbidden("只能修改自己的资料");
        }

        User selfUpdate = new User();
        selfUpdate.setId(currentUserId);
        selfUpdate.setName(user.getName());
        selfUpdate.setSex(user.getSex());
        selfUpdate.setBirthday(user.getBirthday());
        selfUpdate.setIdNum(user.getIdNum());
        selfUpdate.setTelephone(user.getTelephone());
        selfUpdate.setAddress(user.getAddress());
        return service.update(selfUpdate);
    }

    @GetMapping("/selectByUsername/{username}")
    public CommonResult<?> selectByUsername(@PathVariable String username, HttpServletRequest request) {
        if (isStaff(request)) {
            return service.selectByUsername(username);
        }
        User current = currentUser(request);
        return current != null && current.getUsername().equals(username)
                ? service.selectByUsername(username)
                : CommonResult.forbidden();
    }

    @GetMapping("/selectByRid/{rid}")
    public CommonResult<?> selectByRoleId(@PathVariable int rid, HttpServletRequest request) {
        if (!isStaff(request)) {
            return CommonResult.forbidden();
        }
        if (rid < 1 || rid > 4) {
            return CommonResult.validateFailed("用户角色无效");
        }
        return service.selectByRid(rid);
    }

    @GetMapping("/selectByRidByPage/{rid}/{current}/{size}")
    public CommonResult<?> selectByRoleIdByPage(
            @PathVariable int rid,
            @PathVariable int current,
            @PathVariable int size,
            HttpServletRequest request) {
        if (!isStaff(request)) {
            return CommonResult.forbidden();
        }
        if (rid < 1 || rid > 4) {
            return CommonResult.validateFailed("用户角色无效");
        }
        return service.selectByRidByPage(rid, current, size);
    }

    @GetMapping("/selectById/{id}")
    public CommonResult<?> selectById(@PathVariable int id, HttpServletRequest request) {
        Integer current = currentUserId(request);
        if (current == null || (!current.equals(id) && !isStaff(request))) {
            return CommonResult.forbidden();
        }
        return service.selectById(id);
    }

    @GetMapping("/selectByNameOrIdNum/{searchName}")
    public CommonResult<?> selectByNameOrIdNum(
            @PathVariable String searchName,
            HttpServletRequest request) {
        if (!isStaff(request)) {
            return CommonResult.forbidden();
        }
        String query = searchName == null ? "" : searchName.trim();
        if (query.isEmpty() || query.length() > 50) {
            return CommonResult.validateFailed("搜索条件无效");
        }
        return service.selectByNameOrIdNum(query);
    }

    private User currentUser(HttpServletRequest request) {
        Integer id = currentUserId(request);
        return id == null ? null : service.getById(id);
    }

    private Integer currentUserId(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ID_ATTRIBUTE);
        return value instanceof Integer id ? id : null;
    }

    private boolean isAdmin(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        return value instanceof Integer roleId && roleId == 1;
    }

    private boolean isStaff(HttpServletRequest request) {
        Object value = request.getAttribute(JWTInterceptor.USER_ROLE_ATTRIBUTE);
        return value instanceof Integer roleId && roleId >= 1 && roleId <= 3;
    }

}
