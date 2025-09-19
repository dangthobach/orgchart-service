package com.learnmore.domain.migration;

import com.learnmore.domain.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

/**
 * Bảng master lưu trữ thời hạn lưu trữ
 */
@Entity
@Table(name = "retention_period", indexes = {
    @Index(name = "idx_retention_years", columnList = "years", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetentionPeriod extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "years", nullable = false, unique = true)
    private Integer years;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
