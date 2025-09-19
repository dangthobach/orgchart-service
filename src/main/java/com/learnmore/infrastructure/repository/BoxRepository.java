package com.learnmore.infrastructure.repository;

import com.learnmore.domain.migration.Box;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BoxRepository extends JpaRepository<Box, Long> {
    
    Optional<Box> findByCodeAndIsActive(String code, Boolean isActive);
    
    @Query("SELECT b FROM Box b WHERE b.code = :code AND b.isActive = true")
    Optional<Box> findActiveByCode(@Param("code") String code);
    
    boolean existsByCodeAndIsActive(String code, Boolean isActive);
}
