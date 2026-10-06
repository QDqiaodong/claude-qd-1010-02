package com.museum.collection.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.museum.collection.entity.Artifact;

public interface ArtifactRepository extends JpaRepository<Artifact, Long> {

    Optional<Artifact> findByArtifactNo(String artifactNo);

    List<Artifact> findByStatus(String status);

    /** 悲观写锁：交接流转前锁定藏品行，保证同一藏品并发确认交出时只有一张单据生效 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Artifact a where a.id = :id")
    Optional<Artifact> findByIdForUpdate(@Param("id") Long id);
}
