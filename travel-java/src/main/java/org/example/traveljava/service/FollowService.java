package org.example.traveljava.service;

import org.example.traveljava.entity.Follow;
import org.example.traveljava.entity.User;
import org.example.traveljava.repository.FollowRepository;
import org.example.traveljava.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowService.class);

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    public FollowService(FollowRepository followRepository, UserRepository userRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
    }

    public List<Map<String, Object>> getFollowing(Long userId) {
        List<Follow> follows = followRepository.findByFollowerIdOrderByCreatedAtDesc(userId);
        return convertToUserInfoList(follows, "followingId", userId);
    }

    public List<Map<String, Object>> getFollowers(Long userId) {
        List<Follow> follows = followRepository.findByFollowingIdOrderByCreatedAtDesc(userId);
        return convertToUserInfoList(follows, "followerId", userId);
    }

    public int getFollowingCount(Long userId) {
        return followRepository.countByFollowerId(userId);
    }

    public int getFollowersCount(Long userId) {
        return followRepository.countByFollowingId(userId);
    }

    private List<Map<String, Object>> convertToUserInfoList(List<Follow> follows, String userIdField, Long currentUserId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (follows.isEmpty()) {
            return result;
        }

        List<Long> targetIds = follows.stream()
                .map(f -> "followingId".equals(userIdField) ? f.getFollowingId() : f.getFollowerId())
                .toList();

        // 批量作者信息
        Map<Long, User> userMap = userRepository.findAllById(targetIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 批量 isFollowed（当前用户是否关注了这些目标用户）
        Set<Long> followedIds = currentUserId == null ? Set.of()
                : followRepository.findByFollowerIdAndFollowingIdIn(currentUserId, targetIds).stream()
                        .map(Follow::getFollowingId)
                        .collect(Collectors.toSet());

        for (Follow follow : follows) {
            Long targetId = "followingId".equals(userIdField) ? follow.getFollowingId() : follow.getFollowerId();
            User user = userMap.get(targetId);
            if (user == null) continue;
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("nickname", user.getNickname());
            userInfo.put("avatar", user.getAvatar());
            userInfo.put("bio", user.getBio());
            userInfo.put("isFollowed", followedIds.contains(targetId));
            result.add(userInfo);
        }
        return result;
    }

    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("不能关注自己");
        }

        // CTRL-3 修复：插入前校验被关注用户存在，避免关注不存在的用户（脏数据 + 计数虚增）
        if (followingId == null || !userRepository.existsById(followingId)) {
            throw new IllegalArgumentException("关注的用户不存在");
        }

        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new IllegalArgumentException("已经关注过了");
        }

        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);

        try {
            followRepository.save(follow);
        } catch (DataIntegrityViolationException e) {
            // 并发重复关注：唯一约束兜底
            throw new IllegalArgumentException("已经关注过了");
        }
        log.info("关注用户：followerId={}, followingId={}", followerId, followingId);

        // 原子增减计数，避免并发丢失更新
        userRepository.incrementFollowingCount(followerId);
        userRepository.incrementFollowersCount(followingId);
    }

    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        if (!followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new IllegalArgumentException("未关注该用户");
        }

        followRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);
        log.info("取消关注：followerId={}, followingId={}", followerId, followingId);

        userRepository.decrementFollowingCount(followerId);
        userRepository.decrementFollowersCount(followingId);
    }
}
