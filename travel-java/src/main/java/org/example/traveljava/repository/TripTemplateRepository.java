package org.example.traveljava.repository;

import org.example.traveljava.entity.TripTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 【新功能】行程模板仓储
 */
public interface TripTemplateRepository extends JpaRepository<TripTemplate, Long> {

    /** 模板市场：仅已发布模板 */
    Page<TripTemplate> findByStatusOrderByDownloadsDesc(String status, Pageable pageable);

    /** 按关键字搜索（名称/目的地/标签模糊匹配），仅已发布 */
    @Query("select t from TripTemplate t where t.status = :status and " +
            "(t.name like %:kw% or t.destination like %:kw% or t.tags like %:kw%) order by t.downloads desc")
    Page<TripTemplate> searchPublished(@Param("status") String status, @Param("kw") String kw, Pageable pageable);

    /** 原子自增下载数（实例化时调用，避免并发覆盖） */
    @Modifying
    @Query("update TripTemplate t set t.downloads = t.downloads + 1 where t.id = :id")
    int incrementDownloads(@Param("id") Long id);
}
