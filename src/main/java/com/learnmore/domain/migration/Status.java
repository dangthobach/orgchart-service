package com.learnmore.domain.migration;

import com.learnmore.domain.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

/**
 * Bảng master lưu trữ các trạng thái
 */
@Entity
@Table(name = "status", indexes = {
    @Index(name = "idx_status_code_type", columnList = "code, type", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Status extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "code", length = 50, nullable = false)
    private String code;
    
    @Column(name = "name", length = 200, nullable = false)
    private String name;
    
    @Column(name = "type", length = 50, nullable = false)
    private String type; // CASE_PDM, BOX_STATUS, BOX_STATE, LOSS_STATUS, etc.
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
