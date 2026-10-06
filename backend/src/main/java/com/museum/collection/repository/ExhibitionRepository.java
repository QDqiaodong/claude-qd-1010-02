package com.museum.collection.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.museum.collection.entity.Exhibition;

public interface ExhibitionRepository extends JpaRepository<Exhibition, Long> {

    Optional<Exhibition> findByExCode(String exCode);

    List<Exhibition> findByStatus(String status);
}
