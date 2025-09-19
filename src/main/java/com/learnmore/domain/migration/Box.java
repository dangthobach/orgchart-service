package com.learnmore.domain.migration;

import com.learnmore.domain.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Bảng master lưu trữ thông tin thùng
 */
@Entity
@Table(name = "box", indexes = {
    @Index(name = "idx_box_code", columnList = "code", unique = true),
    @Index(name = "idx_box_warehouse_location", columnList = "warehouse_id, location_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Box extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "code", length = 50, nullable = false, unique = true)
    private String code;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "box_status_id")
    private Status boxStatus; // Tình trạng thùng
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "box_state_id")
    private Status boxState; // Trạng thái thùng
    
    @Column(name = "entry_date")
    private LocalDate entryDate; // Ngày nhập kho VPBank
    
    @Column(name = "transfer_date")
    private LocalDate transferDate; // Ngày chuyển kho Crown
    
    @Column(name = "notes", length = 2000)
    private String notes;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
