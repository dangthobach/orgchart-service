package com.learnmore.application.dto;

import com.learnmore.application.utils.ExcelColumn;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Role DTO for Excel processing and fake data generation
 * Contains role information with Excel column mappings
 */
@Entity
@Table(name = "roles", 
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "id"),
           @UniqueConstraint(columnNames = "name")
       })
@Data
@NoArgsConstructor
public class Role {
    
    @Id
    @ExcelColumn(name = "ID")
    @Column(name = "id", nullable = false, unique = true)
    private String id;
    
    @ExcelColumn(name = "Name")
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;
    
    @ExcelColumn(name = "Description")
    @Column(name = "description", length = 500)
    private String description;
    
    @ExcelColumn(name = "Is Active")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
    
    @ExcelColumn(name = "Created At")
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @ExcelColumn(name = "Updated At")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * Constructor for easy creation with all fields
     */
    public Role(String id, String name, String description, Boolean isActive, 
                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    /**
     * Constructor for easy creation with basic fields
     */
    public Role(String name, String description) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }
    
    @Override
    public String toString() {
        return String.format("Role{id='%s', name='%s', description='%s', isActive=%s}", 
                           id, name, description, isActive);
    }
}

