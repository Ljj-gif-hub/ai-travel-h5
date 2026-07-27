package org.example.traveljava.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 行程规划实体类
 * 存储用户生成的完整旅行规划方案，包括预算明细、行程JSON和关联酒店信息
 */
@Entity
@Table(name = "trip_plans")
public class TripPlan {

    /** 行程规划主键ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 目的地城市 */
    @Column(nullable = false, length = 100)
    private String destination;

    /** 出行天数 */
    @Column(nullable = false)
    private Integer days;

    /** 出行人数 */
    @Column(nullable = false)
    private Integer people;

    /** 总预算（元） */
    @Column(name = "total_budget", precision = 12, scale = 2)
    private BigDecimal totalBudget;

    /** 酒店费用（元） */
    @Column(name = "hotel_cost", precision = 12, scale = 2)
    private BigDecimal hotelCost;

    /** 门票费用（元） */
    @Column(name = "ticket_cost", precision = 12, scale = 2)
    private BigDecimal ticketCost;

    /** 餐饮费用（元） */
    @Column(name = "food_cost", precision = 12, scale = 2)
    private BigDecimal foodCost;

    /** 交通费用（元） */
    @Column(name = "transport_cost", precision = 12, scale = 2)
    private BigDecimal transportCost;

    /**
     * 行程详细规划的JSON字符串
     * 包含每日行程安排、景点列表、路线建议等完整数据
     */
    @Column(name = "plan_json", columnDefinition = "CLOB")
    private String planJson;

    /**
     * 关联酒店ID列表，以逗号分隔存储
     * 例如："1,3,5"
     */
    @Column(name = "hotel_ids", length = 500)
    private String hotelIds;

    /**
     * 规划状态
     * "completed" - 已完成
     * "draft"     - 草稿
     * "cancelled" - 已取消
     */
    @Column(length = 20)
    private String status;

    /** 记录创建时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 记录最后更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 实体持久化前自动设置创建时间和更新时间
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = "completed";
        }
    }

    /**
     * 实体更新前自动刷新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== 无参构造 ====================
    public TripPlan() {
    }

    // ==================== Getter / Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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

    public BigDecimal getTotalBudget() {
        return totalBudget;
    }

    public void setTotalBudget(BigDecimal totalBudget) {
        this.totalBudget = totalBudget;
    }

    public BigDecimal getHotelCost() {
        return hotelCost;
    }

    public void setHotelCost(BigDecimal hotelCost) {
        this.hotelCost = hotelCost;
    }

    public BigDecimal getTicketCost() {
        return ticketCost;
    }

    public void setTicketCost(BigDecimal ticketCost) {
        this.ticketCost = ticketCost;
    }

    public BigDecimal getFoodCost() {
        return foodCost;
    }

    public void setFoodCost(BigDecimal foodCost) {
        this.foodCost = foodCost;
    }

    public BigDecimal getTransportCost() {
        return transportCost;
    }

    public void setTransportCost(BigDecimal transportCost) {
        this.transportCost = transportCost;
    }

    public String getPlanJson() {
        return planJson;
    }

    public void setPlanJson(String planJson) {
        this.planJson = planJson;
    }

    public String getHotelIds() {
        return hotelIds;
    }

    public void setHotelIds(String hotelIds) {
        this.hotelIds = hotelIds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
