package com.museum.collection.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.museum.collection.entity.Tour;

public interface TourRepository extends JpaRepository<Tour, Long> {

    Optional<Tour> findByTourNo(String tourNo);

    List<Tour> findByGalleryId(Long galleryId);

    List<Tour> findByStatus(String status);

    /** 悲观写锁：导览状态流转前锁定场次，避免并发重复结束/开始互相覆盖 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tour t where t.id = :id")
    Optional<Tour> findByIdForUpdate(@Param("id") Long id);

    /** 只取展厅 id，避免先把场次实体放入一级缓存后再用悲观锁读到旧状态 */
    @Query("select t.galleryId from Tour t where t.id = :id")
    Optional<Long> findGalleryIdById(@Param("id") Long id);

    /**
     * 锁定当前占用展厅容量的场次。必须在已取得展厅锁后调用，
     * 让容量检查同时看到并发排期/结束后的最新已提交状态。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tour t where t.galleryId = :galleryId and t.status in :statuses")
    List<Tour> findOccupyingByGalleryIdForUpdate(@Param("galleryId") Long galleryId,
                                                 @Param("statuses") List<String> statuses);
}
