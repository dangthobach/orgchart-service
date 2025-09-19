package com.learnmore.domain.migration;

import com.learnmore.domain.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

/**
 * Bảng master lưu trữ vị trí trong kho
 */
@Entity
@Table(name = "location", indexes = {
    @Index(name = "idx_location_area_row_column", columnList = "area, row_num, column_num", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Location extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "area", length = 50, nullable = false)
    private String area; // Khu vực
    
    @Column(name = "row_num", nullable = false)
    private Integer rowNum; // Hàng
    
    @Column(name = "column_num", nullable = false)
    private Integer columnNum; // Cột
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "capacity")
    private Integer capacity;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
