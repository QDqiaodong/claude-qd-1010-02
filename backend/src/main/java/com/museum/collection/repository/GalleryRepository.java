package com.museum.collection.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.museum.collection.entity.Gallery;

public interface GalleryRepository extends JpaRepository<Gallery, Long> {

    Optional<Gallery> findByGalleryCode(String galleryCode);

    List<Gallery> findByStatus(String status);
}
