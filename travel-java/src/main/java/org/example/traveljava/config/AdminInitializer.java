package org.example.traveljava.config;

import org.example.traveljava.entity.User;
import org.example.traveljava.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时若不存在管理员账号则自动创建。
 * 账号密码通过环境变量 ADMIN_USERNAME / ADMIN_PASSWORD 注入（默认 admin / admin123）。
 */
@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository userRepository;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    public AdminInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 内置默认口令（application.yml 兜底值）— 用于识别"未显式配置" */
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername(adminUsername).isPresent()) {
            return;
        }
        if (adminUsername == null || adminUsername.isBlank()) {
            log.warn("未配置 ADMIN_USERNAME，跳过管理员初始化");
            return;
        }
        // 安全：未显式配置 ADMIN_PASSWORD（仍为默认口令）时拒绝启动，
        // 防止带公开默认口令的管理员账号被自动创建。
        if (adminPassword == null || adminPassword.isBlank() || DEFAULT_ADMIN_PASSWORD.equals(adminPassword)) {
            throw new IllegalStateException(
                    "检测到管理员账号将使用默认口令，出于安全考虑已拒绝创建。"
                    + "请显式设置 ADMIN_PASSWORD 环境变量后重启。");
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User admin = new User();
        admin.setUsername(adminUsername.trim());
        admin.setPassword(encoder.encode(adminPassword));
        admin.setRole("ADMIN");
        admin.setStatus(1);
        admin.setNickname("管理员");
        userRepository.save(admin);
        log.info("已创建管理员账号: {}", adminUsername);
    }
}
