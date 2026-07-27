package org.example.traveljava.dto;

import java.util.List;

public class TravelPlanDTO {
    private String destination;
    private Integer days;
    private Long budget;
    private List<DayPlan> dayPlans;
    private BudgetDetail budgetDetail;
    private List<String> tips;

    public TravelPlanDTO() {}

    public TravelPlanDTO(String destination, Integer days, Long budget, List<DayPlan> dayPlans, BudgetDetail budgetDetail, List<String> tips) {
        this.destination = destination;
        this.days = days;
        this.budget = budget;
        this.dayPlans = dayPlans;
        this.budgetDetail = budgetDetail;
        this.tips = tips;
    }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public Long getBudget() { return budget; }
    public void setBudget(Long budget) { this.budget = budget; }
    public List<DayPlan> getDayPlans() { return dayPlans; }
    public void setDayPlans(List<DayPlan> dayPlans) { this.dayPlans = dayPlans; }
    public BudgetDetail getBudgetDetail() { return budgetDetail; }
    public void setBudgetDetail(BudgetDetail budgetDetail) { this.budgetDetail = budgetDetail; }
    public List<String> getTips() { return tips; }
    public void setTips(List<String> tips) { this.tips = tips; }

    public static class DayPlan {
        private Integer day;
        private String dayTitle;
        private List<TimeSlot> timeSlots;

        public DayPlan() {}

        public DayPlan(Integer day, String dayTitle, List<TimeSlot> timeSlots) {
            this.day = day;
            this.dayTitle = dayTitle;
            this.timeSlots = timeSlots;
        }

        public Integer getDay() { return day; }
        public void setDay(Integer day) { this.day = day; }
        public String getDayTitle() { return dayTitle; }
        public void setDayTitle(String dayTitle) { this.dayTitle = dayTitle; }
        public List<TimeSlot> getTimeSlots() { return timeSlots; }
        public void setTimeSlots(List<TimeSlot> timeSlots) { this.timeSlots = timeSlots; }
    }

    public static class TimeSlot {
        private String timeOfDay;
        private String time;
        private String attraction;
        private String activity;
        private String duration;
        private String cost;
        private String transport;
        private String tips;
        private String imageUrl;

        public TimeSlot() {}

        public TimeSlot(String timeOfDay, String time, String attraction, String activity, String duration, String cost, String transport, String tips, String imageUrl) {
            this.timeOfDay = timeOfDay;
            this.time = time;
            this.attraction = attraction;
            this.activity = activity;
            this.duration = duration;
            this.cost = cost;
            this.transport = transport;
            this.tips = tips;
            this.imageUrl = imageUrl;
        }

        public String getTimeOfDay() { return timeOfDay; }
        public void setTimeOfDay(String timeOfDay) { this.timeOfDay = timeOfDay; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public String getAttraction() { return attraction; }
        public void setAttraction(String attraction) { this.attraction = attraction; }
        public String getActivity() { return activity; }
        public void setActivity(String activity) { this.activity = activity; }
        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }
        public String getCost() { return cost; }
        public void setCost(String cost) { this.cost = cost; }
        public String getTransport() { return transport; }
        public void setTransport(String transport) { this.transport = transport; }
        public String getTips() { return tips; }
        public void setTips(String tips) { this.tips = tips; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }

    public static class BudgetDetail {
        private Long transportation;
        private Long accommodation;
        private Long food;
        private Long tickets;
        private Long other;
        private Long total;

        public BudgetDetail() {}

        public BudgetDetail(Long transportation, Long accommodation, Long food, Long tickets, Long other, Long total) {
            this.transportation = transportation;
            this.accommodation = accommodation;
            this.food = food;
            this.tickets = tickets;
            this.other = other;
            this.total = total;
        }

        public Long getTransportation() { return transportation; }
        public void setTransportation(Long transportation) { this.transportation = transportation; }
        public Long getAccommodation() { return accommodation; }
        public void setAccommodation(Long accommodation) { this.accommodation = accommodation; }
        public Long getFood() { return food; }
        public void setFood(Long food) { this.food = food; }
        public Long getTickets() { return tickets; }
        public void setTickets(Long tickets) { this.tickets = tickets; }
        public Long getOther() { return other; }
        public void setOther(Long other) { this.other = other; }
        public Long getTotal() { return total; }
        public void setTotal(Long total) { this.total = total; }
    }
}