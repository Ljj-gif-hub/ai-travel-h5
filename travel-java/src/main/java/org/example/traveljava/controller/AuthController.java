package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.service.RefreshTokenService;
import org.example.traveljava.service.UserService;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@io.swagger.v3.oas.annotations.tags.Tag(name = "认证")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(UserService userService, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    @RateLimit(max = 5, duration = 60, key = "auth_register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> params) {
        try {
            String username = params.get("username");
            String password = params.get("password");
            String confirmPassword = params.get("confirmPassword");
            String phone = params.get("phone");
            String email = params.get("email");

            if (username == null || username.trim().isEmpty()) {
                return Result.fail("用户名不能为空");
            }
            
            if (username.length() < 3 || username.length() > 50) {
                return Result.fail("用户名长度必须在3-50个字符之间");
            }

            if (password == null || password.trim().isEmpty()) {
                return Result.fail("密码不能为空");
            }
            
            if (password.length() < 6 || password.length() > 100) {
                return Result.fail("密码长度必须在6-100个字符之间");
            }

            if (confirmPassword == null || confirmPassword.trim().isEmpty()) {
                return Result.fail("确认密码不能为空");
            }

            if (!password.equals(confirmPassword)) {
                return Result.fail("两次输入的密码不一致");
            }

            if (phone != null && !phone.isEmpty() && !phone.matches("^1[3-9]\\d{9}$")) {
                return Result.fail("手机号格式不正确");
            }

            if (email != null && !email.isEmpty() && !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                return Result.fail("邮箱格式不正确");
            }

            userService.register(username, password, phone, email);

            return Result.ok(Map.of(
                    "message", "注册成功",
                    "username", username
            ));
        } catch (IllegalArgumentException e) {
            log.warn("注册失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("注册异常", e);
            return Result.fail("注册失败，请稍后重试");
        }
    }

    @PostMapping("/login")
    @RateLimit(max = 10, duration = 60, key = "auth_login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        try {
            String username = params.get("username");
            String password = params.get("password");

            if (username == null || username.trim().isEmpty()) {
                return Result.fail("用户名不能为空");
            }
            
            if (username.length() > 50) {
                return Result.fail("用户名长度不能超过50个字符");
            }

            if (password == null || password.trim().isEmpty()) {
                return Result.fail("密码不能为空");
            }
            
            if (password.length() > 100) {
                return Result.fail("密码长度不能超过100个字符");
            }

            Map<String, Object> loginResult = userService.login(username, password);

            return Result.ok(loginResult);
        } catch (IllegalArgumentException e) {
            log.warn("登录失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("登录异常", e);
            return Result.fail("登录失败，请稍后重试");
        }
    }

    /**
     * 【新功能】刷新令牌换新令牌对（旋转刷新）。
     *
     * 前端契约（trval-h5/src/api/index.js refreshAuthToken）：
     *   POST /api/auth/refresh {refreshToken}
     *   → 200 {code:0, data:{token, refreshToken}} 成功；code:-1 或网络失败 → 前端跳登录。
     * 旧 refreshToken 在成功刷新后即失效（Redis 先删后发），并发重放只有一次成功。
     */
    @PostMapping("/refresh")
    @RateLimit(max = 20, duration = 60, key = "auth_refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> params) {
        try {
            Map<String, Object> result = refreshTokenService.refresh(
                    params != null ? params.get("refreshToken") : null);
            return Result.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("刷新令牌失败：{}", e.getMessage());
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("刷新令牌异常", e);
            return Result.fail("刷新失败，请重新登录");
        }
    }

    /**
     * 【新功能】第三方登录（微信/支付宝 OAuth 换登录态）。
     *
     * 当前状态：前端 LoginView 的 OAUTH_CONFIG 中 appid 为空（未申请开放平台应用），
     * 页面在跳转授权前即给出「未配置」提示，不会请求本端点。后端同样未配置渠道。
     * 完整实现路径：申请应用资质后配置 social.wechat.app-id/app-secret（支付宝为
     * social.alipay.*），在本端点实现 code → openid 换取 → 按 openid 绑定/自动注册
     * 用户 → 签发 token + refreshToken（与 login 同构，含用户名冲突处理）。
     * 未配置时返回明确业务提示，避免 404 假象。
     */
    @PostMapping("/social-login")
    @RateLimit(max = 5, duration = 60, key = "auth_social")
    public Result<Map<String, Object>> socialLogin(@RequestBody Map<String, String> params) {
        log.warn("收到第三方登录请求但未配置任何 OAuth 渠道: platform={}",
                params != null ? params.getOrDefault("platform", "?") : "?");
        return Result.fail("第三方登录未配置，请使用账号密码登录");
    }
}
