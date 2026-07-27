package org.example.traveljava.repository;

import org.example.traveljava.entity.SavedTravelPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedTravelPlanRepository extends JpaRepository<SavedTravelPlan, Long> {

    List<SavedTravelPlan> findAllByOrderByCreatedAtDesc();

    List<SavedTravelPlan> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndId(Long userId, Long id);
}
