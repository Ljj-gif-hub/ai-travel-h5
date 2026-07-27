package org.example.traveljava.service;

import org.example.traveljava.entity.User;
import org.example.traveljava.repository.UserRepository;
import org.example.traveljava.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public UserService(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Transactional
    public User register(String username, String password, String phone, String email) {
        log.info("用户注册：username={}", username);

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        if (phone != null && !phone.isEmpty() && userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("手机号已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone(phone);
        user.setEmail(email);
        user.setRole("USER");
        user.setStatus(1);
        user.setNickname(username);

        User savedUser = userRepository.save(user);
        log.info("用户注册成功：id={}, username={}", savedUser.getId(), savedUser.getUsername());
        return savedUser;
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
            user.setPhone((String) params.get("phone"));
        }
        if (params.containsKey("email")) {
            user.setEmail((String) params.get("email"));
        }

        User savedUser = userRepository.save(user);
        log.info("用户更新资料：id={}", userId);
        return savedUser;
    }

    @Transactional
    public void logout(String token) {
        log.info("用户退出登录");
    }
}
