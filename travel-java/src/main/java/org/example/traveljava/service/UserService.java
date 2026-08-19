package org.example.traveljava.service;

import org.example.traveljava.entity.User;
import org.example.traveljava.repository.UserRepository;
import org.example.traveljava.util.JwtUtil;
import org.example.traveljava.util.TokenBlacklist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TokenBlacklist tokenBlacklist;
    private final RefreshTokenService refreshTokenService;

    public UserService(UserRepository userRepository, JwtUtil jwtUtil, TokenBlacklist tokenBlacklist,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.tokenBlacklist = tokenBlacklist;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public User register(String username, String password, String phone, String email) {
        log.info("用户注册：username={}", username);

        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        if (phone != null && !phone.isEmpty() && userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("手机号已被注册");
        }

        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone(phone);
        user.setEmail(email);
        user.setRole("USER");
        user.setStatus(1);
        user.setNickname(username.trim());

        try {
            User savedUser = userRepository.save(user);
            log.info("用户注册成功：id={}, username={}", savedUser.getId(), savedUser.getUsername());
            return savedUser;
        } catch (DataIntegrityViolationException e) {
            // 并发注册同用户名/手机号：唯一约束兜底
            throw new IllegalArgumentException("用户名或手机号已被注册");
        }
    }

    public Map<String, Object> login(String username, String password) {
        log.info("用户登录：username={}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (user.getStatus() != 1) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        log.info("用户登录成功：id={}, username={}", user.getId(), user.getUsername());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("bio", user.getBio());
        userInfo.put("phone", user.getPhone());
        userInfo.put("email", user.getEmail());
        userInfo.put("role", user.getRole());
        userInfo.put("level", user.getLevel());
        userInfo.put("points", user.getPoints());
        userInfo.put("following", user.getFollowingCount());
        userInfo.put("followers", user.getFollowersCount());
        userInfo.put("travelNotes", user.getNotesCount());
        userInfo.put("citiesVisited", user.getCitiesVisited());
        userInfo.put("totalDays", user.getTotalDays());
        userInfo.put("totalSpent", user.getTotalSpent());
        userInfo.put("totalPhotos", user.getTotalPhotos());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", userInfo);

        // 【新功能】刷新令牌：Redis 故障时降级为不发 refreshToken（登录本身不阻断），
        // 前端收不到 refreshToken 即走纯 access token 模式（过期后重新登录）。
        try {
            result.put("refreshToken", refreshTokenService.issue(user.getId()));
        } catch (Exception e) {
            log.warn("签发刷新令牌失败（Redis 不可用？），本次登录降级为无刷新令牌: {}", e.getMessage());
        }

        return result;
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    @Transactional
    public User updateProfile(Long userId, Map<String, Object> params) {
        User user = getUserById(userId);

        if (params.containsKey("nickname")) {
            user.setNickname((String) params.get("nickname"));
        }
        if (params.containsKey("avatar")) {
            user.setAvatar((String) params.get("avatar"));
        }
        if (params.containsKey("bio")) {
            user.setBio((String) params.get("bio"));
        }
        if (params.containsKey("phone")) {
            String phone = (String) params.get("phone");
            if (phone != null && !phone.trim().isEmpty()) {
                String target = phone.trim();
                userRepository.findByPhone(target)
                        .filter(other -> !other.getId().equals(userId))
                        .ifPresent(other -> {
                            throw new IllegalArgumentException("该手机号已被其他账号使用");
                        });
                user.setPhone(target);
            }
        }
        if (params.containsKey("email")) {
            user.setEmail((String) params.get("email"));
        }

        User savedUser = userRepository.save(user);
        log.info("用户更新资料：id={}", userId);
        return savedUser;
    }

    @Transactional
    public void logout(String token, String refreshToken) {
        // 把 token 加入黑名单，使其立即失效（到 token 自然过期为止）
        try {
            long ttl = jwtUtil.extractExpiration(token).getTime() - System.currentTimeMillis();
            tokenBlacklist.blacklist(token, ttl);
            log.info("用户退出登录，token 已加入黑名单");
        } catch (Exception e) {
            // 黑名单失败不阻断退出（token 过期后自然失效）
            log.warn("退出登录黑名单失效失败: {}", e.getMessage());
        }

        // 【L-TOKEN-1】退出登录 = 全局注销：吊销该账号所有刷新令牌（含被窃取的旧令牌），
        // 不再只撤销携带的那一个。token 未过期时能解出 userId（extractUserId 只读 claim 不校验），
        // 解析失败则退回撤销携带的单个 refreshToken。
        Long userId = null;
        try {
            userId = jwtUtil.extractUserId(token);
        } catch (Exception e) {
            log.warn("退出登录提取 userId 失败，退回单令牌撤销: {}", e.getMessage());
        }
        if (userId != null) {
            refreshTokenService.revokeAll(userId);
        } else {
            refreshTokenService.revoke(refreshToken);
        }
    }

    /* ==================== 【新功能】积分与等级 ==================== */

    /** 等级门槛：青铜 0 / 白银 100 / 黄金 300 / 铂金 800 / 钻石 2000 */
    public static final int[] LEVEL_THRESHOLDS = {0, 100, 300, 800, 2000};
    public static final String[] LEVEL_NAMES = {"青铜", "白银", "黄金", "铂金", "钻石"};

    /** 按积分计算等级名 */
    public static String levelOf(Integer points) {
        int p = points == null ? 0 : points;
        String level = LEVEL_NAMES[0];
        for (int i = 0; i < LEVEL_THRESHOLDS.length; i++) {
            if (p >= LEVEL_THRESHOLDS[i]) {
                level = LEVEL_NAMES[i];
            }
        }
        return level;
    }

    /** 下一等级名（已到最高级返回 null） */
    public static String nextLevelOf(Integer points) {
        int p = points == null ? 0 : points;
        for (int i = 0; i < LEVEL_THRESHOLDS.length; i++) {
            if (p < LEVEL_THRESHOLDS[i]) {
                return LEVEL_NAMES[i];
            }
        }
        return null;
    }

    /** 距离下一等级还差多少分（已到最高级返回 0） */
    public static int pointsToNextLevel(Integer points) {
        int p = points == null ? 0 : points;
        for (int threshold : LEVEL_THRESHOLDS) {
            if (p < threshold) {
                return threshold - p;
            }
        }
        return 0;
    }

    /**
     * 积分原子增减 + 刷新等级字段（发帖+5 / 评论+2 / 支付成功+10 / 笔记被赞+1）。
     * 调用方保证只对"首次生效"的动作用增量发放（如支付仅当 markPaidIfPending 返回 1），避免重复发奖。
     */
    @Transactional
    public void addPoints(Long userId, int delta) {
        if (userId == null || delta == 0) return;
        userRepository.addPoints(userId, delta);
        // 刷新等级字段（best-effort：失败不影响积分本身）
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                String level = levelOf(user.getPoints());
                if (!level.equals(user.getLevel())) {
                    user.setLevel(level);
                    userRepository.save(user);
                }
            }
        } catch (Exception e) {
            log.warn("刷新用户等级失败: userId={}, err={}", userId, e.getMessage());
        }
    }

    /** 当前用户等级信息（积分、等级名、下一级及所需差值） */
    public Map<String, Object> getUserLevel(Long userId) {
        User user = getUserById(userId);
        int points = user.getPoints() == null ? 0 : user.getPoints();
        Map<String, Object> result = new HashMap<>();
        result.put("points", points);
        result.put("level", levelOf(points));
        result.put("nextLevel", nextLevelOf(points));
        result.put("pointsToNextLevel", pointsToNextLevel(points));
        return result;
    }
}
