package org.example.traveljava.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.entity.Post;
import org.example.traveljava.entity.PostLike;
import org.example.traveljava.entity.User;
import org.example.traveljava.repository.PostLikeRepository;
import org.example.traveljava.repository.PostRepository;
import org.example.traveljava.repository.UserRepository;
import org.example.traveljava.util.TextCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final UserService userService;
    private final ContentModerationService moderationService;

    public PostService(PostRepository postRepository, PostLikeRepository postLikeRepository, UserRepository userRepository,
                       UserService userService, ContentModerationService moderationService) {
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.moderationService = moderationService;
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
        // 【新功能】社区广场过滤被举报隐藏的动态
        org.springframework.data.domain.Page<Post> postPage = postRepository
                .findByHiddenFalseOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(safePage, safeSize));
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
        // 【修复】存储前 OWASP 白名单消毒，防存储型 XSS
        post.setContent(TextCleaner.sanitizeHtml((String) params.get("content")));

        if (params.containsKey("images")) {
            try {
                post.setImages(objectMapper.writeValueAsString(params.get("images")));
            } catch (JsonProcessingException e) {
                log.warn("序列化图片列表失败", e);
            }
        }

        post.setLikes(0);
        post.setComments(0);

        // 【新功能】内容审核（开关控制，LLM 失败 fail-open）
        ContentModerationService.ModerationResult m = moderationService.check(post.getContent());
        if (!m.isSafe()) {
            throw new IllegalArgumentException("内容包含违规信息，请修改后重试");
        }

        Post saved = postRepository.save(post);
        // 【新功能】发帖 +5 积分
        userService.addPoints(userId, 5);
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
     * 【并发安全】点赞数走原子 UPDATE（delta ±1），不再 count()+save() 读改写
     */
    @Transactional
    public Map<String, Object> toggleLike(Long postId, Long userId) {
        postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("动态不存在"));

        boolean alreadyLiked = postLikeRepository.existsByPostIdAndUserId(postId, userId);

        if (alreadyLiked) {
            // 取消点赞：计数原子 -1（likes>=0 条件下不会减成负数）
            postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            postRepository.adjustLikes(postId, -1);
        } else {
            // LIKE-1 修复：原子 INSERT IGNORE 按受影响行数判断，冲突不再走 save()+catch
            //（IDENTITY 下冲突会标记 rollback-only，catch 后提交仍抛 UnexpectedRollbackException）
            int inserted = postLikeRepository.insertIfAbsent(postId, userId);
            if (inserted > 0) {
                postRepository.adjustLikes(postId, 1);
            }
        }

        // adjustLikes 已 clearAutomatically，重新读取最新计数
        Post fresh = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("动态不存在"));
        Map<String, Object> result = new HashMap<>();
        result.put("likes", fresh.getLikes());
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
