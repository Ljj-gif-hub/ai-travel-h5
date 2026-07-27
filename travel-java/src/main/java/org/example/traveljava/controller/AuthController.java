package org.example.traveljava.controller;

import org.example.traveljava.annotation.RateLimit;
import org.example.traveljava.service.UserService;
import org.example.traveljava.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
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
}
