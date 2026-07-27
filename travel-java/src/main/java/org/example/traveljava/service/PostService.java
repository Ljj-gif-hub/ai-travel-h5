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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;

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
     * 【修复】社区广场：返回所有用户的动态（而非仅当前用户）
     * 每篇动态附带作者信息（昵称、头像）和当前用户是否已点赞
     */
    public List<Map<String, Object>> getPosts(Long currentUserId) {
        List<Post> posts = postRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Post post : posts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", post.getId());
            item.put("content", post.getContent());
            item.put("images", post.getImages());
            item.put("likes", post.getLikes());
            item.put("comments", post.getComments());

            // 作者信息
            userRepository.findById(post.getUserId()).ifPresent(author -> {
                item.put("authorName", author.getNickname() != null ? author.getNickname() : author.getUsername());
                item.put("authorAvatar", author.getAvatar());
            });

            // 当前用户是否已点赞
            if (currentUserId != null) {
                item.put("isLiked", postLikeRepository.existsByPostIdAndUserId(post.getId(), currentUserId));
            } else {
                item.put("isLiked", false);
            }

            // 日期
            item.put("date", post.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            result.add(item);
        }
        return result;
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
            // 点赞
            postLikeRepository.save(new PostLike(postId, userId));
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
