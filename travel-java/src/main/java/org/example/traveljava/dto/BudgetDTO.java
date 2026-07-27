package org.example.traveljava.dto;

/**
 * 预算明细 SSE 分片 DTO
 */
public class BudgetDTO {
    private int trafficCost;
    private int hotelCost;
    private int foodCost;
    private int otherCost;
    private int totalBudget;
    private String eventType = "budget-update";

    public BudgetDTO() {}

    public BudgetDTO(int traffic, int hotel, int food, int other, int total) {
        this.trafficCost = traffic; this.hotelCost = hotel;
        this.foodCost = food; this.otherCost = other; this.totalBudget = total;
    }

    public int getTrafficCost() { return trafficCost; }
    public int getHotelCost() { return hotelCost; }
    public int getFoodCost() { return foodCost; }
    public int getOtherCost() { return otherCost; }
    public int getTotalBudget() { return totalBudget; }
    public String getEventType() { return eventType; }
}
