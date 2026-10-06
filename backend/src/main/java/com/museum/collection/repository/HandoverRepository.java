package com.museum.collection.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.museum.collection.entity.Handover;

public interface HandoverRepository extends JpaRepository<Handover, Long> {

    Optional<Handover> findByHandoverNo(String handoverNo);

    List<Handover> findByStatusOrderByIdDesc(String status);

    List<Handover> findByArtifactIdOrderByIdDesc(Long artifactId);

    List<Handover> findByExhibitionIdOrderByIdDesc(Long exhibitionId);

    List<Handover> findByArtifactIdAndStatusIn(Long artifactId, List<String> statuses);

    List<Handover> findByExhibitionIdAndStatusIn(Long exhibitionId, List<String> statuses);

    /** 悲观写锁：状态流转前锁定单据行，避免与「展览闭幕终止」等并发写互相覆盖 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Handover h where h.id = :id")
    Optional<Handover> findByIdForUpdate(@Param("id") Long id);
}
