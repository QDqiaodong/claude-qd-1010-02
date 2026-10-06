package com.museum.collection.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.museum.collection.entity.PointAdjustment;

public interface PointAdjustmentRepository extends JpaRepository<PointAdjustment, Long> {

    Optional<PointAdjustment> findByApplyNo(String applyNo);

    List<PointAdjustment> findByStatusOrderByIdDesc(String status);

    List<PointAdjustment> findByMemberIdOrderByIdDesc(Long memberId);

    List<PointAdjustment> findByMemberIdAndStatusOrderByIdDesc(Long memberId, String status);

    /** 悲观写锁：复核 / 撤回 / 修正前锁定申请行，并发复核在此排队，后到者读到已处理结果 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PointAdjustment p where p.id = :id")
    Optional<PointAdjustment> findByIdForUpdate(@Param("id") Long id);
}
