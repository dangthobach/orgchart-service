package com.learnmore.infrastructure.repository;

import com.learnmore.domain.migration.CaseDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CaseDetailRepository extends JpaRepository<CaseDetail, Long> {
    
    @Query("SELECT COUNT(cd) FROM CaseDetail cd WHERE cd.unit.code = :unitCode AND cd.box.code = :boxCode AND cd.docDate = :docDate AND cd.quantity = :quantity")
    Long countByBusinessKey(@Param("unitCode") String unitCode, 
                           @Param("boxCode") String boxCode, 
                           @Param("docDate") LocalDate docDate, 
                           @Param("quantity") Integer quantity);
    
    @Query("SELECT cd FROM CaseDetail cd WHERE cd.unit.code = :unitCode AND cd.box.code = :boxCode")
    List<CaseDetail> findByUnitAndBox(@Param("unitCode") String unitCode, @Param("boxCode") String boxCode);
}
