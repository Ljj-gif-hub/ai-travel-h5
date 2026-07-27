package org.example.traveljava.dto;

import java.util.List;

/**
 * 出行贴士 SSE 分片 DTO
 */
public class TripTipsDTO {
    private List<String> tipList;
    private String eventType = "tips-update";

    public TripTipsDTO() {}

    public TripTipsDTO(List<String> tips) { this.tipList = tips; }

    public List<String> getTipList() { return tipList; }
    public void setTipList(List<String> tipList) { this.tipList = tipList; }
    public String getEventType() { return eventType; }
}
