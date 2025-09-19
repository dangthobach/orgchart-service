package com.learnmore.infrastructure.repository;

import com.learnmore.domain.migration.RetentionPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RetentionPeriodRepository extends JpaRepository<RetentionPeriod, Long> {
    
    Optional<RetentionPeriod> findByYearsAndIsActive(Integer years, Boolean isActive);
    
    @Query("SELECT rp FROM RetentionPeriod rp WHERE rp.years = :years AND rp.isActive = true")
    Optional<RetentionPeriod> findActiveByYears(@Param("years") Integer years);
    
    boolean existsByYearsAndIsActive(Integer years, Boolean isActive);
}
