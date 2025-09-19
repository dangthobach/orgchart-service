package com.learnmore.infrastructure.repository;

import com.learnmore.domain.migration.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    
    Optional<Warehouse> findByCodeAndIsActive(String code, Boolean isActive);
    
    @Query("SELECT w FROM Warehouse w WHERE w.code = :code AND w.isActive = true")
    Optional<Warehouse> findActiveByCode(@Param("code") String code);
    
    boolean existsByCodeAndIsActive(String code, Boolean isActive);
}
