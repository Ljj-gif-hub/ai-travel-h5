package org.example.traveljava.dto;

import java.util.Map;

/**
 * 单日行程 SSE 分片推送 DTO
 */
public class DailyTripDTO {
    private int dayNum;
    private Map<String, Object> dayData;
    private int totalDays;
    private String eventType = "day-update";

    public DailyTripDTO() {}

    public DailyTripDTO(int dayNum, Map<String, Object> dayData, int totalDays) {
        this.dayNum = dayNum;
        this.dayData = dayData;
        this.totalDays = totalDays;
    }

    public int getDayNum() { return dayNum; }
    public void setDayNum(int dayNum) { this.dayNum = dayNum; }

    public Map<String, Object> getDayData() { return dayData; }
    public void setDayData(Map<String, Object> dayData) { this.dayData = dayData; }

    public int getTotalDays() { return totalDays; }
    public void setTotalDays(int totalDays) { this.totalDays = totalDays; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
}
