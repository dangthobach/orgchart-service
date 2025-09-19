package com.learnmore.infrastructure.repository;

import com.learnmore.domain.migration.DocType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocTypeRepository extends JpaRepository<DocType, Long> {
    
    Optional<DocType> findByNameAndIsActive(String name, Boolean isActive);
    
    @Query("SELECT dt FROM DocType dt WHERE dt.name = :name AND dt.isActive = true")
    Optional<DocType> findActiveByName(@Param("name") String name);
    
    boolean existsByNameAndIsActive(String name, Boolean isActive);
}
