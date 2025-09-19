package com.learnmore.infrastructure.repository;

import com.learnmore.domain.migration.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {
    
    Optional<Unit> findByCodeAndIsActive(String code, Boolean isActive);
    
    @Query("SELECT u FROM Unit u WHERE u.code = :code AND u.isActive = true")
    Optional<Unit> findActiveByCode(@Param("code") String code);
    
    boolean existsByCodeAndIsActive(String code, Boolean isActive);
}
