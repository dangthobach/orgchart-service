package com.learnmore.infrastructure.repository;

import com.learnmore.domain.migration.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StatusRepository extends JpaRepository<Status, Long> {
    
    Optional<Status> findByCodeAndTypeAndIsActive(String code, String type, Boolean isActive);
    
    @Query("SELECT s FROM Status s WHERE s.code = :code AND s.type = :type AND s.isActive = true")
    Optional<Status> findActiveByCodeAndType(@Param("code") String code, @Param("type") String type);
    
    List<Status> findByTypeAndIsActive(String type, Boolean isActive);
    
    boolean existsByCodeAndTypeAndIsActive(String code, String type, Boolean isActive);
}
