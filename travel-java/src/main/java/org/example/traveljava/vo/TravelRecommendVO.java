package org.example.traveljava.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class TravelRecommendVO {

    @NotBlank(message = "目的地不能为空")
    private String destination;

    @Min(value = 100, message = "预算不能低于100元")
    private Long budget;

    @Min(value = 1, message = "天数不能少于1天")
    private Integer days;

    private String message;

    public TravelRecommendVO() {
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public Long getBudget() {
        return budget;
    }

    public void setBudget(Long budget) {
        this.budget = budget;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
