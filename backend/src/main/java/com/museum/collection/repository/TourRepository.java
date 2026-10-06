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

    /**
     * 加锁前的只读投影：只拿归属展厅 id / 编号，不把导览实体装进一级缓存，
     * 从而保证随后 findByIdForUpdate 的加锁读装配出的是最新行而不是缓存旧值。
     */
    @Query("select t.galleryId as galleryId, t.tourNo as tourNo from Tour t where t.id = :id")
    Optional<TourRef> findRefById(@Param("id") Long id);

    interface TourRef {
        Long getGalleryId();

        String getTourNo();
    }

    /**
     * 悲观写锁：状态流转前锁定导览行，两个工作人员同时操作时后到者基于最新状态判定。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tour t where t.id = :id")
    Optional<Tour> findByIdForUpdate(@Param("id") Long id);

    /**
     * 悲观写锁 + 当前读：锁展厅行之后再以本查询锁定该展厅所有占用容量的导览行并取最新版本计数。
     * 必须是加锁读——MySQL 默认 RR 隔离级别下普通计数会用事务快照，可能漏掉其它事务刚提交的
     * 结束/排期；当前读保证拿到的就是锁内最新容量。走 idx_tour_gallery_status 只锁匹配记录。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tour t where t.galleryId = :galleryId and t.status in :statuses")
    List<Tour> findOccupyingForUpdate(@Param("galleryId") Long galleryId,
                                      @Param("statuses") List<String> statuses);
}
