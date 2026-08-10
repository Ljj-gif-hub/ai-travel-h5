package org.example.traveljava.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.entity.Post;
import org.example.traveljava.entity.PostLike;
import org.example.traveljava.entity.User;
import org.example.traveljava.repository.PostLikeRepository;
import org.example.traveljava.repository.PostRepository;
import org.example.traveljava.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PostService(PostRepository postRepository, PostLikeRepository postLikeRepository, UserRepository userRepository) {
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
        this.userRepository = userRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 【修复】社区广场：分页返回所有用户的动态（而非全表加载）
     * 每篇动态附带作者信息（昵称、头像、userId）和当前用户是否已点赞
     * 批量查询作者与点赞，消除 N+1；images 反序列化为数组返回
     *
     * @return { list, total, page, size, hasMore }
     */
    public Map<String, Object> getPosts(Long currentUserId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        org.springframework.data.domain.Page<Post> postPage = postRepository
                .findAllByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(safePage, safeSize));
        List<Post> posts = postPage.getContent();
        List<Map<String, Object>> result = new ArrayList<>();

        // 批量作者信息
        List<Long> authorIds = posts.stream().map(Post::getUserId).distinct().toList();
        Map<Long, User> authorMap = authorIds.isEmpty() ? Map.of()
                : userRepository.findAllById(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        // 批量点赞状态
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Set<Long> likedPostIds = currentUserId == null || postIds.isEmpty() ? Set.of()
                : postLikeRepository.findByPostIdInAndUserId(postIds, currentUserId).stream()
                        .map(PostLike::getPostId)
                        .collect(Collectors.toSet());

        for (Post post : posts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", post.getId());
            item.put("userId", post.getUserId());
            item.put("content", post.getContent());
            item.put("images", parseImages(post.getImages()));
            item.put("likes", post.getLikes());
            item.put("comments", post.getComments());

            // 作者信息
            User author = authorMap.get(post.getUserId());
            if (author != null) {
                item.put("authorName", author.getNickname() != null ? author.getNickname() : author.getUsername());
                item.put("authorAvatar", author.getAvatar());
            }

            item.put("isLiked", currentUserId != null && likedPostIds.contains(post.getId()));

            // 日期
            item.put("date", post.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            result.add(item);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("list", result);
        resp.put("total", postPage.getTotalElements());
        resp.put("page", safePage);
        resp.put("size", safeSize);
        resp.put("hasMore", safePage + 1 < postPage.getTotalPages());
        return resp;
    }

    /** 数据库里 images 存的是 JSON 数组字符串，反序列化为数组返回 */
    private List<String> parseImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(imagesJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析动态图片列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Transactional
    public Post createPost(Long userId, Map<String, Object> params) {
        Post post = new Post();
        post.setUserId(userId);
        post.setContent((String) params.get("content"));

        if (params.containsKey("images")) {
            try {
                post.setImages(objectMapper.writeValueAsString(params.get("images")));
            } catch (JsonProcessingException e) {
                log.warn("序列化图片列表失败", e);
            }
        }

        post.setLikes(0);
        post.setComments(0);

        Post saved = postRepository.save(post);
        log.info("创建动态：userId={}", userId);
        return saved;
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("动态不存在"));

        if (!post.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除该动态");
        }

        postRepository.delete(post);
        log.info("删除动态：postId={}", postId);
    }

    /**
     * 【修复】点赞/取消点赞 — 需要登录，按用户追踪，每人只能点赞一次
     */
    @Transactional
    public Map<String, Object> toggleLike(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("动态不存在"));

        boolean alreadyLiked = postLikeRepository.existsByPostIdAndUserId(postId, userId);

        if (alreadyLiked) {
            // 取消点赞
            postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            post.setLikes(postLikeRepository.countByPostId(postId));
            postRepository.save(post);
        } else {
            // 点赞：并发双击时唯一约束兜底，冲突视为已点赞
            try {
                postLikeRepository.save(new PostLike(postId, userId));
            } catch (DataIntegrityViolationException e) {
                log.debug("动态点赞并发冲突：postId={}, userId={}", postId, userId);
            }
            post.setLikes(postLikeRepository.countByPostId(postId));
            postRepository.save(post);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("likes", post.getLikes());
        result.put("isLiked", !alreadyLiked);
        return result;
    }

    /**
     * 按ID获取单篇动态
     */
    public Post getPostById(Long postId) {
        return postRepository.findById(postId).orElse(null);
    }

    /**
     * 检查当前用户是否已点赞某动态
     */
    public boolean isLikedByUser(Long postId, Long userId) {
        if (userId == null) return false;
        return postLikeRepository.existsByPostIdAndUserId(postId, userId);
    }
}
