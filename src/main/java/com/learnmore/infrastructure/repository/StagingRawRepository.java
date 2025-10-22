package com.learnmore.infrastructure.repository;

import com.learnmore.domain.migration.StagingRaw;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StagingRawRepository extends JpaRepository<StagingRaw, UUID> {
    
    List<StagingRaw> findByJobIdOrderByRowNum(String jobId);
    
    @Query("SELECT COUNT(sr) FROM StagingRaw sr WHERE sr.jobId = :jobId")
    Long countByJobId(@Param("jobId") String jobId);
    
    @Query("SELECT COUNT(sr) FROM StagingRaw sr WHERE sr.jobId = :jobId AND sr.parseErrors IS NOT NULL")
    Long countErrorsByJobId(@Param("jobId") String jobId);
    
    @Query("SELECT sr FROM StagingRaw sr WHERE sr.jobId = :jobId AND sr.parseErrors IS NULL ORDER BY sr.rowNum")
    List<StagingRaw> findValidRecordsByJobId(@Param("jobId") String jobId);
    
    @Query("SELECT sr FROM StagingRaw sr WHERE sr.jobId = :jobId AND sr.parseErrors IS NOT NULL ORDER BY sr.rowNum")
    List<StagingRaw> findErrorRecordsByJobId(@Param("jobId") String jobId);
    
    @Modifying
    @Query("DELETE FROM StagingRaw sr WHERE sr.jobId = :jobId")
    void deleteByJobId(@Param("jobId") String jobId);
    
    @Query(value = "SELECT sr.* FROM staging_raw sr WHERE sr.job_id = :jobId " +
           "AND sr.parse_errors IS NULL LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<StagingRaw> findValidRecordsPaginated(@Param("jobId") String jobId, 
                                               @Param("limit") int limit, 
                                               @Param("offset") int offset);
    
    // Methods for error handling with new errorMessage and errorCode columns
    @Query("SELECT sr FROM StagingRaw sr WHERE sr.jobId = :jobId AND sr.errorMessage IS NOT NULL ORDER BY sr.rowNum")
    List<StagingRaw> findByJobIdAndErrorMessageIsNotNull(@Param("jobId") String jobId);
    
    @Query("SELECT COUNT(sr) FROM StagingRaw sr WHERE sr.jobId = :jobId AND sr.errorMessage IS NOT NULL")
    Long countByJobIdAndErrorMessageIsNotNull(@Param("jobId") String jobId);
}
