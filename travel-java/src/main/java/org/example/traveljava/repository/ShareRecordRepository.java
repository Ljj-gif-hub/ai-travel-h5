package org.example.traveljava.repository;

import org.example.traveljava.entity.ShareRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShareRecordRepository extends JpaRepository<ShareRecord, Long> {

    Optional<ShareRecord> findByToken(String token);

    boolean existsByToken(String token);

    /** L-SHARE-1 修复：删除行程计划时级联清理其全部分享短码记录，防孤儿分享链接 */
    void deleteByPlanId(Long planId);
}
