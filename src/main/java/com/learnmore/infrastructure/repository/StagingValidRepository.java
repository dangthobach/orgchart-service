package com.learnmore.infrastructure.repository;

import com.learnmore.domain.migration.StagingValid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StagingValidRepository extends JpaRepository<StagingValid, Long> {
    
    List<StagingValid> findByJobIdOrderByRowNum(String jobId);
    
    @Query("SELECT COUNT(sv) FROM StagingValid sv WHERE sv.jobId = :jobId")
    Long countByJobId(@Param("jobId") String jobId);
    
    @Modifying
    @Query("DELETE FROM StagingValid sv WHERE sv.jobId = :jobId")
    void deleteByJobId(@Param("jobId") String jobId);
    
    @Query(value = "SELECT sv.* FROM staging_valid sv WHERE sv.job_id = :jobId " +
           "LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<StagingValid> findRecordsPaginated(@Param("jobId") String jobId, 
                                           @Param("limit") int limit, 
                                           @Param("offset") int offset);
}
