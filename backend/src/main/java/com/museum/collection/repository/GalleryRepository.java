package com.museum.collection.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.museum.collection.entity.Gallery;

public interface GalleryRepository extends JpaRepository<Gallery, Long> {

    Optional<Gallery> findByGalleryCode(String galleryCode);

    List<Gallery> findByStatus(String status);

    /** 仅取 id 的标量投影：避免加锁前把展厅实体放进一级缓存，保证随后的加锁读装配出最新行 */
    @Query("select g.id from Gallery g where g.galleryCode = :code")
    Optional<Long> findIdByGalleryCode(@Param("code") String code);

    /**
     * 悲观写锁：排期 / 导览状态流转前先锁展厅行，统一所有事务的加锁顺序（展厅 → 导览），
     * 既让并发排期串行化（不会超额），又让“结束释放”和“随后排期”看到一致的容量结果，且不会死锁。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Gallery g where g.id = :id")
    Optional<Gallery> findByIdForUpdate(@Param("id") Long id);
}
