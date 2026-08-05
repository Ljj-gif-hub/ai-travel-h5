package org.example.traveljava.repository;

import org.example.traveljava.entity.ShareRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShareRecordRepository extends JpaRepository<ShareRecord, Long> {

    Optional<ShareRecord> findByToken(String token);

    boolean existsByToken(String token);
}
