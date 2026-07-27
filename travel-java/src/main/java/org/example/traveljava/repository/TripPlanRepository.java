package org.example.traveljava.repository;

import org.example.traveljava.entity.TripPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 行程规划数据访问层
 * 提供行程规划方案的持久化操作，支持按用户和状态分类查询
 */
@Repository
public interface TripPlanRepository extends JpaRepository<TripPlan, Long> {

    /**
     * 查询某用户的所有行程规划，按创建时间倒序排列
     * 用于用户"我的行程"列表展示
     * @param userId 用户ID
     * @return 用户的所有行程规划列表（最新在前）
     */
    List<TripPlan> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 根据用户ID和规划状态查询行程列表
     * 用于按状态筛选（如已完成、草稿、已取消）
     * @param userId 用户ID
     * @param status 规划状态（"completed"、"draft"、"cancelled"）
     * @return 符合条件的行程规划列表
     */
    List<TripPlan> findByUserIdAndStatus(Long userId, String status);
}
