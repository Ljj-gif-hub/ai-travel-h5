package org.example.traveljava.dto;

/**
 * 【新功能】AI 出行打包清单请求
 */
public class PackingListRequest {

    /** 目的地 */
    private String destination;
    /** 出行天数 */
    private Integer days;
    /** 同行人描述（如 "家人" / "2大1小"），可选 */
    private String companion;

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public String getCompanion() { return companion; }
    public void setCompanion(String companion) { this.companion = companion; }
}
