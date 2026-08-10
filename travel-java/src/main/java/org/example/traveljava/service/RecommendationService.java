package org.example.traveljava.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.traveljava.config.AppMetrics;
import org.example.traveljava.entity.Favorite;
import org.example.traveljava.entity.Note;
import org.example.traveljava.entity.NoteLike;
import org.example.traveljava.entity.SavedTravelPlan;
import org.example.traveljava.repository.FavoriteRepository;
import org.example.traveljava.repository.NoteLikeRepository;
import org.example.traveljava.repository.NoteRepository;
import org.example.traveljava.repository.SavedTravelPlanRepository;
import org.example.traveljava.vo.RecommendItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 智能推荐引擎 — 内容推荐 + 用户协同过滤 + 热门兜底 三段式混合推荐。
 *
 * 数据源（全部来自现有业务表，无需额外埋点）：
 *  1. 收藏（favorites）—— 用户显式行为，权重最高
 *  2. 游记点赞（note_likes）—— 隐含偏好
 *  3. 保存的行程（saved_travel_plans）—— 目的地级偏好
 *
 * 算法：
 *  - 用户画像：把用户收藏/点赞/行程的文本（名称+描述+标签+目的地）切分为 token 权重向量；
 *  - 内容分：候选与用户画像的 token 重叠度；
 *  - 协同分：收藏同一物品的其他用户（兴趣邻居）还收藏了什么 → 加权提升；
 *  - 热门分：全站收藏/点赞热度，无画像时兜底；
 *  - 综合分 = 内容分×3 + 协同分×1.5 + 热门分×0.5。
 *
 * 候选池聚合结果缓存 5 分钟（Caffeine），个性化评分每次实时计算。
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final Pattern SEPARATOR = Pattern.compile("[\\s,、，。；：()（）\\[\\]{}|/\\\\《》【】-]+");
    private static final Pattern CN_ONLY = Pattern.compile("^[\\u4e00-\\u9fa5]+$");
    private static final Set<String> DESTINATION_TYPES = Set.of("destination", "city", "城市", "目的地");

    private final FavoriteRepository favoriteRepository;
    private final NoteRepository noteRepository;
    private final NoteLikeRepository noteLikeRepository;
    private final SavedTravelPlanRepository savedPlanRepository;
    private final CityService cityService;
    private final AppMetrics appMetrics;

    /** 候选池缓存：TTL 5 分钟 */
    private final Cache<String, CandidatePool> poolCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(8)
            .build();

    /** 已保存行程目的地热度缓存：TTL 5 分钟，避免每次推荐全表扫描 */
    private final Cache<String, Map<String, Double>> savedPlanDestCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(1)
            .build();

    public RecommendationService(FavoriteRepository favoriteRepository,
                                 NoteRepository noteRepository,
                                 NoteLikeRepository noteLikeRepository,
                                 SavedTravelPlanRepository savedPlanRepository,
                                 CityService cityService,
                                 AppMetrics appMetrics) {
        this.favoriteRepository = favoriteRepository;
        this.noteRepository = noteRepository;
        this.noteLikeRepository = noteLikeRepository;
        this.savedPlanRepository = savedPlanRepository;
        this.cityService = cityService;
        this.appMetrics = appMetrics;
    }

    /* ============================================================
     * 公开入口
     * ============================================================ */

    /**
     * 通用推荐：登录用户个性化，未登录返回热门。
     * @param userId     可为 null（未登录）
     * @param typeFilter 限定条目类型（如 attraction / destination / note），null 表示不限
     * @param cityFilter 限定城市（匹配名称/文本包含），null 表示不限
     */
    public List<RecommendItem> recommend(Long userId, String typeFilter, String cityFilter, int limit) {
        CandidatePool pool = getPool();
        Map<String, Double> profile = userId == null ? Map.of() : buildUserProfile(userId);

        List<Scored> scored = new ArrayList<>();
        for (Candidate c : pool.items) {
            if (typeFilter != null && !typeFilter.isBlank()
                    && !c.targetType.equalsIgnoreCase(typeFilter) && !c.targetType.contains(typeFilter)) {
                continue;
            }
            if (cityFilter != null && !cityFilter.isBlank() && !c.text.contains(cityFilter)) {
                continue;
            }
            if (userId != null && pool.userKeys(userId).contains(c.key())) {
                continue; // 过滤已交互过的
            }
            double content = contentScore(profile, tokenize(c.text));
            double cf = userId == null ? 0 : collaborativeBoost(pool, userId, c);
            double pop = Math.log1p(c.popularity);
            double total = content * 3.0 + cf * 1.5 + pop * 0.5;
            if (total <= 0) total = pop;
            scored.add(new Scored(c, total, reasonFor(content, cf)));
        }
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        appMetrics.recommendServed();
        return scored.stream().limit(limit)
                .map(s -> new RecommendItem(s.c().targetType, s.c().targetId, s.c().name, s.c().cover,
                        Math.round(s.score() * 100.0) / 100.0, s.reason()))
                .toList();
    }

    /** 目的地推荐：行程目的地 + 城市类收藏 + 全站热门城市兜底 */
    public List<RecommendItem> recommendDestinations(Long userId, int limit) {
        CandidatePool pool = getPool();
        Map<String, Double> profile = userId == null ? Map.of() : buildUserProfile(userId);

        // 1) 从已保存行程聚合目的地热度（缓存 5 分钟，避免每请求全表扫描）
        Map<String, Double> destPop = new LinkedHashMap<>(savedPlanDestPopularity());
        Map<String, String> destCover = new LinkedHashMap<>();
        destPop.keySet().forEach(d -> destCover.put(d, ""));
        // 2) 城市/目的地类收藏聚合
        for (Candidate c : pool.items) {
            if (DESTINATION_TYPES.contains(c.targetType.toLowerCase())) {
                addTo(destPop, c.name, c.popularity);
                destCover.putIfAbsent(c.name, c.cover);
            }
        }
        // 3) 热门城市兜底（确保冷启动也有内容）
        Set<String> seen = new HashSet<>(destPop.keySet());
        for (String city : cityService.getHotCityNames()) {
            if (!seen.add(city)) continue;
            destPop.put(city, 1.0);
            destCover.put(city, "");
        }

        List<Scored> scored = new ArrayList<>();
        for (Map.Entry<String, Double> e : destPop.entrySet()) {
            String name = e.getKey();
            if (userId != null && pool.userKeys(userId).contains("destination:" + name)) continue;
            double content = contentScore(profile, tokenize(name));
            double total = content * 3.0 + Math.log1p(e.getValue()) * 0.5;
            Candidate c = new Candidate("destination", null, name, destCover.getOrDefault(name, ""), name);
            scored.add(new Scored(c, total, content > 0 ? "根据你的行程偏好推荐" : "热门目的地"));
        }
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        appMetrics.recommendServed();
        return scored.stream().limit(limit)
                .map(s -> new RecommendItem(s.c().targetType, s.c().targetId, s.c().name, s.c().cover,
                        Math.round(s.score() * 100.0) / 100.0, s.reason()))
                .toList();
    }

    /* ============================================================
     * 候选池构建（缓存）
     * ============================================================ */

    private CandidatePool getPool() {
        return poolCache.get("pool", k -> buildPool());
    }

    private CandidatePool buildPool() {
        Map<String, Candidate> byKey = new LinkedHashMap<>();

        // 收藏类候选：全站收藏聚合（名称/描述为画像与协同信号）
        List<Favorite> favs = favoriteRepository.findAll();
        for (Favorite f : favs) {
            Candidate c = byKey.computeIfAbsent(key(f), k -> new Candidate(
                    f.getTargetType(), f.getTargetId(),
                    nvl(f.getTargetName()), nvl(f.getTargetCover()),
                    nvl(f.getTargetName()) + " " + nvl(f.getDescription())));
            c.popularity += 1.0;
            if (f.getLikes() != null && f.getLikes() > 0) c.popularity += f.getLikes() * 0.1;
            c.users.add(f.getUserId());
        }

        // 游记类候选：已发布游记，标签+标题为画像信号，点赞者为协同邻居
        List<Note> notes = noteRepository.findByStatusOrderByCreatedAtDesc("published");
        for (Note n : notes) {
            Candidate c = byKey.computeIfAbsent("note:" + n.getId(), k -> new Candidate(
                    "note", n.getId(), nvl(n.getTitle()), nvl(n.getCover()),
                    nvl(n.getTags()) + " " + nvl(n.getTitle())));
            double pop = (n.getLikes() == null ? 0 : n.getLikes()) * 5.0
                    + (n.getViews() == null ? 0 : n.getViews()) * 0.05;
            c.popularity += Math.max(0.5, pop);
        }
        List<NoteLike> noteLikes = noteLikeRepository.findAll();
        for (NoteLike nl : noteLikes) {
            Candidate c = byKey.get("note:" + nl.getNoteId());
            if (c != null) c.users.add(nl.getUserId());
        }

        CandidatePool pool = new CandidatePool(byKey);
        log.info("[推荐] 候选池构建完成：{} 条（收藏驱动 + {} 篇游记 + 协同邻居 {} 人）",
                byKey.size(), notes.size(), noteLikes.size());
        return pool;
    }

    private String key(Favorite f) {
        String type = f.getTargetType() == null ? "unknown" : f.getTargetType();
        String id = f.getTargetId() == null ? nvl(f.getTargetName()) : String.valueOf(f.getTargetId());
        return type.toLowerCase() + ":" + id;
    }

    /** 已保存行程 → 目的地热度（缓存，5 分钟） */
    private Map<String, Double> savedPlanDestPopularity() {
        return savedPlanDestCache.get("destPop", k -> {
            Map<String, Double> pop = new HashMap<>();
            for (SavedTravelPlan p : savedPlanRepository.findAllByOrderByCreatedAtDesc()) {
                if (p.getDestination() == null || p.getDestination().isBlank()) continue;
                addTo(pop, p.getDestination(), 2.0);
            }
            return pop;
        });
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static void addTo(Map<String, Double> m, String key, double delta) {
        m.put(key, m.getOrDefault(key, 0.0) + delta);
    }

    /* ============================================================
     * 画像与打分
     * ============================================================ */

    /** 用户画像 token 权重：收藏名称/描述、点赞游记标签/标题、已保存行程目的地 */
    private Map<String, Double> buildUserProfile(Long userId) {
        Map<String, Double> profile = new HashMap<>();
        for (Favorite f : favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            addTokens(profile, f.getTargetName(), 2.0);
            addTokens(profile, f.getDescription(), 1.0);
        }
        List<NoteLike> likes = noteLikeRepository.findByUserId(userId);
        if (!likes.isEmpty()) {
            List<Long> noteIds = new ArrayList<>();
            for (NoteLike nl : likes) noteIds.add(nl.getNoteId());
            for (Note n : noteRepository.findAllById(noteIds)) {
                addTokens(profile, n.getTags(), 1.5);
                addTokens(profile, n.getTitle(), 1.0);
            }
        }
        for (SavedTravelPlan p : savedPlanRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            addTokens(profile, p.getDestination(), 2.0);
        }
        return profile;
    }

    /** 内容分：候选文本 token 与画像重叠的加权和（按候选长度轻微归一化） */
    private double contentScore(Map<String, Double> profile, Map<String, Double> itemTokens) {
        if (profile.isEmpty() || itemTokens.isEmpty()) return 0;
        double sum = 0;
        for (Map.Entry<String, Double> e : itemTokens.entrySet()) {
            Double w = profile.get(e.getKey());
            if (w != null) sum += w * e.getValue();
        }
        return sum > 0 ? sum / (1 + itemTokens.size() * 0.1) : 0;
    }

    private Map<String, Double> tokenize(String text) {
        Map<String, Double> m = new HashMap<>();
        if (text == null) return m;
        addTokens(m, text, 1.0);
        return m;
    }

    private void addTokens(Map<String, Double> profile, String text, double weight) {
        if (text == null || text.isBlank()) return;
        for (String part : SEPARATOR.split(text.toLowerCase())) {
            if (part.isEmpty()) continue;
            if (CN_ONLY.matcher(part).matches()) {
                if (part.length() == 1) {
                    addTo(profile, part, weight);
                } else {
                    for (int i = 0; i < part.length() - 1; i++) {
                        addTo(profile, part.substring(i, i + 2), weight);
                    }
                }
            } else {
                addTo(profile, part, weight);
            }
        }
    }

    /** 协同分：候选与用户已收藏物品之间有多少共同收藏者（兴趣邻居） */
    private double collaborativeBoost(CandidatePool pool, Long userId, Candidate c) {
        Set<String> userKeys = pool.userKeys(userId);
        if (userKeys.isEmpty() || c.users.isEmpty()) return 0;
        int shared = 0;
        for (String k : userKeys) {
            Candidate uc = pool.byKey.get(k);
            if (uc == null || uc.users.isEmpty()) continue;
            for (Long uid : uc.users) {
                if (!uid.equals(userId) && c.users.contains(uid)) shared++;
            }
        }
        return Math.min(shared, 30);
    }

    private String reasonFor(double content, double cf) {
        if (content > 0 && cf > 0) return "兴趣相似的用户也喜欢";
        if (content > 0) return "根据你的收藏偏好推荐";
        if (cf > 0) return "你关注的人也在看";
        return "热门推荐";
    }

    /* ============================================================
     * 内部模型
     * ============================================================ */

    private static final class Candidate {
        final String targetType;
        final Long targetId;
        final String name;
        final String cover;
        final String text;
        double popularity;
        final Set<Long> users = new HashSet<>();

        Candidate(String targetType, Long targetId, String name, String cover, String text) {
            this.targetType = targetType == null ? "unknown" : targetType;
            this.targetId = targetId;
            this.name = name;
            this.cover = cover;
            this.text = text;
        }

        String key() {
            return targetType.toLowerCase() + ":" + (targetId == null ? name : targetId);
        }
    }

    private static final class CandidatePool {
        final Map<String, Candidate> byKey;
        final List<Candidate> items;
        final Map<Long, Set<String>> userKeysCache = new HashMap<>();

        CandidatePool(Map<String, Candidate> byKey) {
            this.byKey = byKey;
            this.items = new ArrayList<>(byKey.values());
        }

        Set<String> userKeys(Long userId) {
            Set<String> cached = userKeysCache.get(userId);
            if (cached != null) return cached;
            Set<String> keys = new HashSet<>();
            for (Candidate c : items) {
                if (c.users.contains(userId)) keys.add(c.key());
            }
            userKeysCache.put(userId, keys);
            return keys;
        }
    }

    private record Scored(Candidate c, double score, String reason) {
    }
}
