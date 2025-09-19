package com.learnmore.infrastructure.repository;

import com.learnmore.domain.migration.StagingError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StagingErrorRepository extends JpaRepository<StagingError, Long> {
    
    List<StagingError> findByJobIdOrderByRowNum(String jobId);
    
    @Query("SELECT COUNT(se) FROM StagingError se WHERE se.jobId = :jobId")
    Long countByJobId(@Param("jobId") String jobId);
    
    @Query("SELECT se FROM StagingError se WHERE se.jobId = :jobId AND se.errorType = :errorType ORDER BY se.rowNum")
    List<StagingError> findByJobIdAndErrorType(@Param("jobId") String jobId, @Param("errorType") String errorType);
    
    @Modifying
    @Query("DELETE FROM StagingError se WHERE se.jobId = :jobId")
    void deleteByJobId(@Param("jobId") String jobId);
    
    @Query("SELECT se.errorType, COUNT(se) FROM StagingError se WHERE se.jobId = :jobId GROUP BY se.errorType")
    List<Object[]> getErrorStatsByJobId(@Param("jobId") String jobId);
}
