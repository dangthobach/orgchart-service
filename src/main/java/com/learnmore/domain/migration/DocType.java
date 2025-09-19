package com.learnmore.domain.migration;

import com.learnmore.domain.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

/**
 * Bảng master lưu trữ loại chứng từ
 */
@Entity
@Table(name = "doc_type", indexes = {
    @Index(name = "idx_doc_type_code", columnList = "code", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocType extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "code", length = 50, nullable = false, unique = true)
    private String code;
    
    @Column(name = "name", length = 200, nullable = false)
    private String name;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Column(name = "retention_years")
    private Integer retentionYears;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
