package org.example.traveljava.repository;

import org.example.traveljava.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type);
    List<Order> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    int countByUserIdAndStatus(Long userId, String status);
    int countByUserId(Long userId);
}
