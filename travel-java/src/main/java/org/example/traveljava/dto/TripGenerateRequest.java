package org.example.traveljava.dto;

/**
 * 行程生成请求数据传输对象
 * 接收前端行程规划表单提交的参数，用于触发AI行程自动生成
 */
public class TripGenerateRequest {

    /** 目的地城市（必填） */
    private String destination;

    /** 出行天数（必填） */
    private Integer days;

    /** 出行人数（必填） */
    private Integer people;

    /**
     * 用户偏好设置，JSON格式字符串
     * 可包含：同行伙伴、偏好风格、酒店档次、行程节奏等
     * 示例：{"companion":"家庭","styles":["亲子","休闲"],"hotelLevel":"四星级","pace":"轻松"}
     */
    private String preferences;

    /** 总预算（元） */
    private Long budget;

    // ==================== 无参构造 ====================
    public TripGenerateRequest() {
    }

    // ==================== Getter / Setter ====================

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    public Integer getPeople() {
        return people;
    }

    public void setPeople(Integer people) {
        this.people = people;
    }

    public String getPreferences() {
        return preferences;
    }

    public void setPreferences(String preferences) {
        this.preferences = preferences;
    }

    public Long getBudget() {
        return budget;
    }

    public void setBudget(Long budget) {
        this.budget = budget;
    }
}
