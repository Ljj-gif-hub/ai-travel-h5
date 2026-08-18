package org.example.traveljava.repository;

import org.example.traveljava.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByPhone(String phone);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    // ---- 关注/粉丝计数原子增减，避免并发丢失更新 ----

    @Modifying
    @Query("update User u set u.followingCount = u.followingCount + 1 where u.id = :id")
    int incrementFollowingCount(@Param("id") Long id);

    @Modifying
    @Query("update User u set u.followersCount = u.followersCount + 1 where u.id = :id")
    int incrementFollowersCount(@Param("id") Long id);

    @Modifying
    @Query("update User u set u.followingCount = case when u.followingCount > 0 then u.followingCount - 1 else 0 end where u.id = :id")
    int decrementFollowingCount(@Param("id") Long id);

    @Modifying
    @Query("update User u set u.followersCount = case when u.followersCount > 0 then u.followersCount - 1 else 0 end where u.id = :id")
    int decrementFollowersCount(@Param("id") Long id);

    // ---- 【新功能】积分原子增减（发帖/评论/支付/被赞），clearAutomatically 保证后续读取拿到最新值 ----

    @Modifying(clearAutomatically = true)
    @Query("update User u set u.points = u.points + :delta where u.id = :id")
    int addPoints(@Param("id") Long id, @Param("delta") int delta);
}
