package com.learnmore.domain.migration;

import com.learnmore.domain.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Bảng chính lưu trữ thông tin case detail từ Excel
 */
@Entity
@Table(name = "case_detail", indexes = {
    @Index(name = "idx_case_detail_business_key", 
           columnList = "unit_id, box_id, doc_date, quantity", unique = true),
    @Index(name = "idx_case_detail_dates", columnList = "doc_date, due_date, handover_date"),
    @Index(name = "idx_case_detail_box", columnList = "box_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseDetail extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_type_id", nullable = false)
    private DocType docType;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "box_id", nullable = false)
    private Box box;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retention_period_id")
    private RetentionPeriod retentionPeriod;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_status_id")
    private Status caseStatus; // Trạng thái case PDM
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loss_status_id")
    private Status lossStatus; // Tình trạng thất lạc
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_status_id")
    private Status returnStatus; // Tình trạng không hoàn trả
    
    @Column(name = "responsibility", length = 200)
    private String responsibility; // Trách nhiệm bàn giao
    
    @Column(name = "doc_date", nullable = false)
    private LocalDate docDate; // Ngày chứng từ
    
    @Column(name = "case_title", length = 500)
    private String caseTitle; // Tên tập
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity; // Số lượng tập
    
    @Column(name = "due_date")
    private LocalDate dueDate; // Ngày phải bàn giao
    
    @Column(name = "handover_date")
    private LocalDate handoverDate; // Ngày bàn giao
    
    @Column(name = "case_notes", length = 2000)
    private String caseNotes; // Ghi chú case PDM
    
    @Column(name = "general_notes", length = 2000)
    private String generalNotes; // Lưu ý
}
