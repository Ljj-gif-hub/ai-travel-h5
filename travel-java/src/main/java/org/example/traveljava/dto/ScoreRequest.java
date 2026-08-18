package org.example.traveljava.dto;

/**
 * 【新功能】AI 行程评分请求
 */
public class ScoreRequest {

    /** 目的地 */
    private String destination;
    /** 出行天数 */
    private Integer days;
    /** 行程内容（前端行程 JSON 对象，可为任意结构） */
    private Object planContent;

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public Object getPlanContent() { return planContent; }
    public void setPlanContent(Object planContent) { this.planContent = planContent; }
}
