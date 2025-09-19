package com.learnmore.infrastructure.repository;

import com.learnmore.domain.migration.MigrationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MigrationJobRepository extends JpaRepository<MigrationJob, Long> {
    
    Optional<MigrationJob> findByJobId(String jobId);
    
    List<MigrationJob> findByStatusOrderByCreatedAtDesc(String status);
    
    List<MigrationJob> findByCreatedByOrderByCreatedAtDesc(String createdBy);
    
    @Query("SELECT mj FROM MigrationJob mj WHERE mj.createdAt >= :startDate ORDER BY mj.createdAt DESC")
    List<MigrationJob> findRecentJobs(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT COUNT(mj) FROM MigrationJob mj WHERE mj.status = :status")
    Long countByStatus(@Param("status") String status);
    
    @Query("SELECT mj FROM MigrationJob mj WHERE mj.status IN ('STARTED', 'INGESTING', 'VALIDATING', 'APPLYING') ORDER BY mj.createdAt")
    List<MigrationJob> findRunningJobs();
}
