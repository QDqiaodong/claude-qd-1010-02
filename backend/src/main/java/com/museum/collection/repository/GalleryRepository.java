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

    /**
     * 悲观写锁：排期或结束导览时锁定对应展厅。
     * 同一展厅的容量检查、占用增加和释放按此锁串行，避免并发超容量或重复释放。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Gallery g where g.galleryCode = :galleryCode")
    Optional<Gallery> findByGalleryCodeForUpdate(@Param("galleryCode") String galleryCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Gallery g where g.id = :id")
    Optional<Gallery> findByIdForUpdate(@Param("id") Long id);
}
