package com.learnmore.infrastructure.repository;

import com.learnmore.domain.migration.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
    
    Optional<Location> findByAreaAndRowNumAndColumnNumAndIsActive(String area, Integer rowNum, Integer columnNum, Boolean isActive);
    
    @Query("SELECT l FROM Location l WHERE l.area = :area AND l.rowNum = :rowNum AND l.columnNum = :columnNum AND l.isActive = true")
    Optional<Location> findActiveByPosition(@Param("area") String area, @Param("rowNum") Integer rowNum, @Param("columnNum") Integer columnNum);
    
    boolean existsByAreaAndRowNumAndColumnNumAndIsActive(String area, Integer rowNum, Integer columnNum, Boolean isActive);
}
