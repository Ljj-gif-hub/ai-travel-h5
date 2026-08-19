package org.example.traveljava.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.traveljava.dto.SavedPlanRequest;
import org.example.traveljava.entity.SavedTravelPlan;
import org.example.traveljava.repository.SavedTravelPlanRepository;
import org.example.traveljava.repository.ShareRecordRepository;
import org.example.traveljava.repository.TripShareRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SavedTravelPlanService {

    private static final Logger log = LoggerFactory.getLogger(SavedTravelPlanService.class);

    private final SavedTravelPlanRepository repository;
    private final ObjectMapper objectMapper;
    private final ShareRecordRepository shareRecordRepository;
    private final TripShareRepository tripShareRepository;

    public SavedTravelPlanService(SavedTravelPlanRepository repository, ObjectMapper objectMapper,
                                  ShareRecordRepository shareRecordRepository,
                                  TripShareRepository tripShareRepository) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.shareRecordRepository = shareRecordRepository;
        this.tripShareRepository = tripShareRepository;
    }

    public SavedTravelPlan savePlan(Long userId, SavedPlanRequest request) {
        try {
            SavedTravelPlan plan = new SavedTravelPlan();
            plan.setUserId(userId);
            plan.setDestination(request.getDestination());
            plan.setDays(request.getDays());
            plan.setBudget(request.getBudget());
            plan.setPeople(request.getPeople());
            plan.setPlanJson(objectMapper.writeValueAsString(request.getPlanData()));
            plan.setSource(request.getSource() != null ? request.getSource() : "trip");

            SavedTravelPlan saved = repository.save(plan);
            log.info("保存旅行规划成功：id={}, userId={}, destination={}", saved.getId(), userId, saved.getDestination());
            return saved;
        } catch (Exception e) {
            log.error("保存旅行规划失败", e);
            throw new RuntimeException("保存规划失败：" + e.getMessage());
        }
    }

    /**
     * 【修复】严格按 userId 过滤，禁止返回全量数据
     * 根因：旧代码 userId==null 时返回 repository.findAll()，导致未登录用户可看到所有人的行程
     */
    public List<SavedTravelPlan> getAllPlans(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public SavedTravelPlan getPlanById(Long userId, Long id) {
        if (userId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        SavedTravelPlan plan = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("规划不存在")); // L-SHARE-1 修复：不暴露内部主键 id（防 id 枚举探活）

        if (!userId.equals(plan.getUserId())) {
            throw new RuntimeException("无权访问该规划");
        }

        return plan;
    }

    /**
     * 分享专用：按 id 只读返回行程摘要（绕过属主校验）— 仅用于分享链接公开访问
     * 返回只读快照（不含 userId），不暴露属主信息
     */
    public Map<String, Object> getPlanPublic(Long id) {
        SavedTravelPlan plan = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("规划不存在")); // L-SHARE-1 修复：公开链接不泄露内部主键 id
        return toResponseMap(plan);
    }

    public void deletePlan(Long userId, Long id) {
        if (userId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        SavedTravelPlan plan = getPlanById(userId, id);
        // L-SHARE-1 修复：级联清理该行程的全部分享记录（永久短码 + 24h 分享），防删除后孤儿分享链接继续可访问
        shareRecordRepository.deleteByPlanId(id);
        tripShareRepository.deleteByPlanId(id);
        repository.deleteById(id);
        log.info("删除旅行规划：id={}, userId={}", id, userId);
    }

    /* ==================== 【新功能】ICS 日历导出 ==================== */

    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 从 planJson 构建 iCalendar 内容：每天一个 VEVENT，标题「第N天-城市」，
     * 日期浮动（从今天起按天顺延），描述含当天景点摘要。
     * planJson 结构兼容 dayPlans/days/schedule 等常见字段。
     */
    public String buildIcs(SavedTravelPlan plan) {
        List<DaySummary> days = extractDays(plan);
        if (days.isEmpty()) {
            throw new IllegalArgumentException("行程内容为空，无法导出日历");
        }
        LocalDate start = LocalDate.now();
        StringBuilder sb = new StringBuilder(512);
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//TravelJava//TripPlan//CN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:").append(icsEscape(plan.getDestination())).append("行程\r\n");

        for (int i = 0; i < days.size(); i++) {
            DaySummary d = days.get(i);
            int dayNum = d.day > 0 ? d.day : (i + 1);
            LocalDate dayDate = start.plusDays(dayNum - 1L);
            String title = (d.title != null && !d.title.isBlank())
                    ? d.title
                    : ("第" + dayNum + "天-" + plan.getDestination());
            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:").append(plan.getId()).append("-day").append(dayNum).append("@travel-java\r\n");
            sb.append("DTSTAMP:").append(LocalDate.now().format(ICS_DATE)).append("T000000Z\r\n");
            sb.append("DTSTART;VALUE=DATE:").append(dayDate.format(ICS_DATE)).append("\r\n");
            sb.append("DTEND;VALUE=DATE:").append(dayDate.plusDays(1).format(ICS_DATE)).append("\r\n");
            sb.append("SUMMARY:").append(icsEscape(title)).append("\r\n");
            if (d.description != null && !d.description.isBlank()) {
                for (String line : foldLine("DESCRIPTION:" + icsEscape(d.description))) {
                    sb.append(line).append("\r\n");
                }
            }
            sb.append("END:VEVENT\r\n");
        }
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    /** 一天行程摘要 */
    private static class DaySummary {
        int day;
        String title;
        String description;
    }

    /** 从 planJson 提取每天摘要（兼容多种前端结构） */
    private List<DaySummary> extractDays(SavedTravelPlan plan) {
        List<DaySummary> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(plan.getPlanJson());
            if (root == null) return result;
            JsonNode daysNode = findDaysNode(root);
            if (daysNode == null || !daysNode.isArray()) {
                // 兜底：整个 JSON 当 1 天
                DaySummary single = new DaySummary();
                single.day = 1;
                single.description = summarizeNode(root, 0);
                result.add(single);
                return result;
            }
            int i = 0;
            for (JsonNode el : daysNode) {
                if (el == null || !el.isObject()) continue;
                i++;
                DaySummary d = new DaySummary();
                JsonNode dayNum = el.get("day");
                d.day = dayNum != null && dayNum.isNumber() ? dayNum.asInt() : i;
                d.title = textOf(el, "dayTitle", "title", "theme", "name");
                d.description = summarizeNode(el, 0);
                result.add(d);
            }
        } catch (Exception e) {
            log.warn("解析 planJson 提取天数失败: id={}", plan.getId(), e);
        }
        return result;
    }

    /** 查找天数数组节点（兼容 dayPlans/days/schedule/dailyPlans 等字段名） */
    private JsonNode findDaysNode(JsonNode node) {
        if (node == null) return null;
        String[] candidates = {"dayPlans", "days", "schedule", "dailyPlans", "itinerary", "plan"};
        for (String key : candidates) {
            JsonNode n = node.get(key);
            if (n != null && n.isArray()) return n;
            if (n != null && n.isObject()) {
                JsonNode inner = findDaysNode(n);
                if (inner != null && inner.isArray()) return inner;
            }
        }
        return null;
    }

    /** 汇总一天内的景点文本（timeSlots/activities/attractions 等），深度限制 depth */
    private String summarizeNode(JsonNode node, int depth) {
        if (node == null || depth > 3) return "";
        List<String> parts = new ArrayList<>();
        if (node.isObject()) {
            JsonNode slots = node.get("timeSlots");
            if (slots != null && slots.isArray()) {
                for (JsonNode s : slots) {
                    String attraction = textOf(s, "attraction");
                    if (attraction == null) attraction = textOf(s, "name");
                    if (attraction != null && !attraction.isBlank()) parts.add(attraction);
                }
            }
            if (parts.isEmpty()) {
                for (String key : new String[]{"activities", "attractions", "spots"}) {
                    JsonNode arr = node.get(key);
                    if (arr != null && arr.isArray()) {
                        for (JsonNode a : arr) {
                            if (a.isTextual()) parts.add(a.asText());
                            else {
                                String name = textOf(a, "name", "title", "attraction");
                                if (name != null) parts.add(name);
                            }
                        }
                    }
                }
            }
        }
        return String.join("；", parts);
    }

    /** 依次尝试字段名取文本值 */
    private String textOf(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v != null && v.isTextual() && !v.asText().isBlank()) return v.asText();
        }
        return null;
    }

    /** ICS 文本转义：反斜杠、分号、逗号、换行 */
    private String icsEscape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    /** 按 RFC 5545 折行：每行不超过 75 字节，续行以空格开头 */
    private List<String> foldLine(String line) {
        List<String> lines = new ArrayList<>();
        while (line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 75) {
            int cut = 75;
            // ICS-1 修复：按字符截时 cut 不能超过字符串长度（40 个汉字 = 120 字节但只有 40 字符，substring(0,75) 会越界）
            cut = Math.min(cut, line.length());
            // 按字符回退到安全边界，避免切断多字节字符
            while (cut > 0
                    && line.substring(0, cut).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 74) {
                cut--;
            }
            if (cut <= 0) cut = 30;
            lines.add(line.substring(0, cut));
            line = " " + line.substring(cut);
        }
        lines.add(line);
        return lines;
    }

    public Map<String, Object> toResponseMap(SavedTravelPlan plan) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", plan.getId());
        map.put("destination", plan.getDestination());
        map.put("days", plan.getDays());
        map.put("budget", plan.getBudget());
        map.put("people", plan.getPeople());
        map.put("source", plan.getSource() != null ? plan.getSource() : "trip");
        map.put("createdAt", plan.getCreatedAt());

        try {
            map.put("planData", objectMapper.readValue(plan.getPlanJson(), Object.class));
        } catch (Exception e) {
            log.warn("解析 planJson 失败：id={}", plan.getId(), e);
            map.put("planData", null);
        }

        return map;
    }
}
