package org.example.traveljava.service;

import org.example.traveljava.entity.Follow;
import org.example.traveljava.entity.User;
import org.example.traveljava.repository.FollowRepository;
import org.example.traveljava.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        for (Follow follow : follows) {
            Long targetId = "followingId".equals(userIdField) ? follow.getFollowingId() : follow.getFollowerId();
            userRepository.findById(targetId).ifPresent(user -> {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("nickname", user.getNickname());
                userInfo.put("avatar", user.getAvatar());
                userInfo.put("bio", user.getBio());
                userInfo.put("isFollowed", followRepository.existsByFollowerIdAndFollowingId(currentUserId, targetId));
                result.add(userInfo);
            });
        }
        return result;
    }

    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("不能关注自己");
        }

        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new IllegalArgumentException("已经关注过了");
        }

        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);

        followRepository.save(follow);
        log.info("关注用户：followerId={}, followingId={}", followerId, followingId);

        userRepository.findById(followerId).ifPresent(user -> {
            user.setFollowingCount(user.getFollowingCount() + 1);
            userRepository.save(user);
        });

        userRepository.findById(followingId).ifPresent(user -> {
            user.setFollowersCount(user.getFollowersCount() + 1);
            userRepository.save(user);
        });
    }

    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        if (!followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new IllegalArgumentException("未关注该用户");
        }

        followRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);
        log.info("取消关注：followerId={}, followingId={}", followerId, followingId);

        userRepository.findById(followerId).ifPresent(user -> {
            user.setFollowingCount(Math.max(0, user.getFollowingCount() - 1));
            userRepository.save(user);
        });

        userRepository.findById(followingId).ifPresent(user -> {
            user.setFollowersCount(Math.max(0, user.getFollowersCount() - 1));
            userRepository.save(user);
        });
    }
}
