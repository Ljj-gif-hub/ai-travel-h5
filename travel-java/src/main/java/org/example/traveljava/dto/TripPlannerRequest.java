package org.example.traveljava.dto;

import java.util.List;
import java.util.Map;

/**
 * AI 行程规划器专用请求
 * 从前端 AITripPlanner.vue 表单提交
 */
public class TripPlannerRequest {

    /** 目的地（必填） */
    private String destination;
    /** 出发地 */
    private String origin = "深圳";
    /** 出行天数（必填，1-7） */
    private Integer days;
    /** 出行月份（选填，多选） */
    private List<Integer> months;
    /** 预算（选填，默认5000） */
    private Long budget = 5000L;
    /** 出行偏好 */
    private Map<String, Object> preferences;

    public TripPlannerRequest() {}

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }

    public List<Integer> getMonths() { return months; }
    public void setMonths(List<Integer> months) { this.months = months; }

    public Long getBudget() { return budget; }
    public void setBudget(Long budget) { this.budget = budget; }

    public Map<String, Object> getPreferences() { return preferences; }
    public void setPreferences(Map<String, Object> preferences) { this.preferences = preferences; }

    /**
     * 将偏好 map 转成人类可读的中文提示
     */
    public String buildPreferencePrompt() {
        if (preferences == null || preferences.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        Object companion = preferences.get("companion");
        if (companion != null && !companion.toString().isEmpty()) {
            sb.append("同行伙伴：").append(companion).append("；");
        }
        Object styles = preferences.get("styles");
        if (styles instanceof List && !((List<?>) styles).isEmpty()) {
            sb.append("偏好风格：").append(String.join("、", (List<String>) styles)).append("；");
        }
        Object hotel = preferences.get("hotelLevel");
        if (hotel != null && !hotel.toString().isEmpty()) {
            sb.append("酒店档次：").append(hotel).append("；");
        }
        Object cabin = preferences.get("cabinClass");
        if (cabin != null && !cabin.toString().isEmpty()) {
            sb.append("飞机舱位：").append(cabin).append("；");
        }
        Object pace = preferences.get("pace");
        if (pace != null && !pace.toString().isEmpty()) {
            sb.append("行程节奏：").append(pace).append("；");
        }
        Object schedule = preferences.get("schedule");
        if (schedule != null && !schedule.toString().isEmpty()) {
            sb.append("时间偏好：").append(schedule).append("；");
        }
        return sb.toString();
    }
}
