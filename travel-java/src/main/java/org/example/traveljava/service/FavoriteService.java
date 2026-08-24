package org.example.traveljava.service;

import org.example.traveljava.entity.Favorite;
import org.example.traveljava.repository.FavoriteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class FavoriteService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteService.class);

    private final FavoriteRepository favoriteRepository;

    public FavoriteService(FavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
    }

    public List<Favorite> getFavorites(Long userId) {
        return getFavorites(userId, 0, 20);
    }

    /** 分页获取（page 从 0 开始，修复全表加载） */
    public List<Favorite> getFavorites(Long userId, int page, int size) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .getContent();
    }

    public List<Favorite> getFavoritesByType(Long userId, String type) {
        return getFavoritesByType(userId, type, 0, 20);
    }

    public List<Favorite> getFavoritesByType(Long userId, String type, int page, int size) {
        return favoriteRepository.findByUserIdAndTargetTypeOrderByCreatedAtDesc(userId, type, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .getContent();
    }

    public int getFavoriteCount(Long userId) {
        return favoriteRepository.countByUserId(userId);
    }

    public int getFavoriteCountByType(Long userId, String type) {
        return favoriteRepository.countByUserIdAndTargetType(userId, type);
    }

    @Transactional
    public Favorite addFavorite(Long userId, Map<String, Object> params) {
        // CTRL-4 修复：targetId 为 null 或非数字时不再 NPE/CCE，返回明确业务错误
        Object targetIdObj = params.get("targetId");
        if (!(targetIdObj instanceof Number targetIdNum)) {
            throw new IllegalArgumentException("targetId 不能为空且必须为数字");
        }
        Long targetId = targetIdNum.longValue();
        String targetType = (String) params.get("targetType");

        if (favoriteRepository.existsByUserIdAndTargetIdAndTargetType(userId, targetId, targetType)) {
            throw new IllegalArgumentException("已经收藏过了");
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTargetId(targetId);
        favorite.setTargetType(targetType);
        
        if (params.containsKey("targetName")) {
            favorite.setTargetName((String) params.get("targetName"));
        }
        if (params.containsKey("targetCover")) {
            favorite.setTargetCover((String) params.get("targetCover"));
        }
        if (params.containsKey("rating")) {
            favorite.setRating(((Number) params.get("rating")).doubleValue());
        }
        if (params.containsKey("author")) {
            favorite.setAuthor((String) params.get("author"));
        }
        if (params.containsKey("likes")) {
            favorite.setLikes(((Number) params.get("likes")).intValue());
        }
        if (params.containsKey("description")) {
            favorite.setDescription((String) params.get("description"));
        }

        Favorite saved = favoriteRepository.save(favorite);
        log.info("添加收藏：userId={}, targetId={}, targetType={}", userId, targetId, targetType);
        return saved;
    }

    @Transactional
    public void removeFavorite(Long userId, Long targetId, String targetType) {
        Favorite favorite = favoriteRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, targetType)
                .orElseThrow(() -> new IllegalArgumentException("收藏不存在"));
        favoriteRepository.delete(favorite);
        log.info("取消收藏：userId={}, targetId={}, targetType={}", userId, targetId, targetType);
    }

    @Transactional
    public void deleteFavoriteById(Long userId, Long favoriteId) {
        Favorite favorite = favoriteRepository.findById(favoriteId)
                .orElseThrow(() -> new IllegalArgumentException("收藏不存在"));
        if (!favorite.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除");
        }
        favoriteRepository.delete(favorite);
        log.info("删除收藏：id={}", favoriteId);
    }
}
