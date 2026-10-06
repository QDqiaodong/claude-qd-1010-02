package com.museum.collection.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.museum.collection.entity.Tour;

public interface TourRepository extends JpaRepository<Tour, Long> {

    Optional<Tour> findByTourNo(String tourNo);

    List<Tour> findByGalleryId(Long galleryId);

    List<Tour> findByStatus(String status);

    long countByGalleryId(Long galleryId);
}
