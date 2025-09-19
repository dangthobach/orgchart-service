package com.learnmore.domain.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

/**
 * Bảng master lưu trữ thông tin đơn vị
 */
@Entity
@Table(name = "unit", indexes = {
    @Index(name = "idx_unit_code", columnList = "code", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Unit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "code", length = 50, nullable = false, unique = true)
    private String code;
    
    @Column(name = "name", length = 200, nullable = false)
    private String name;
    
    @Column(name = "parent_code", length = 50)
    private String parentCode;
    
    @Column(name = "level", nullable = false)
    private Integer level = 1;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
