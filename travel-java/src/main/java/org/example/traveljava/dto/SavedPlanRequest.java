package org.example.traveljava.dto;

public class SavedPlanRequest {

    private String destination;
    private Integer days;
    private Long budget;
    private Integer people;
    private Object planData;
    private String source;

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public Long getBudget() { return budget; }
    public void setBudget(Long budget) { this.budget = budget; }
    public Integer getPeople() { return people; }
    public void setPeople(Integer people) { this.people = people; }
    public Object getPlanData() { return planData; }
    public void setPlanData(Object planData) { this.planData = planData; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
