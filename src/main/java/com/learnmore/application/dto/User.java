package com.learnmore.application.dto;

import com.learnmore.application.utils.ExcelColumn;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * User entity for Excel processing performance test
 * Contains 10 fields with ID and IdentityCard as unique fields
 */
@Entity
@Table(name = "users", 
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "id"),
           @UniqueConstraint(columnNames = "identity_card")
       })
@Data
@NoArgsConstructor
public class User {
    
    @Id
    @ExcelColumn(name = "ID", required = true, maxLength = 50, dataType = ExcelColumn.ColumnType.STRING, 
                description = "Mã định danh người dùng", example = "USER001", position = "A")
    @Column(name = "id", nullable = false, unique = true)
    private String id;
    
    @ExcelColumn(name = "Identity Card", required = true, maxLength = 20, dataType = ExcelColumn.ColumnType.STRING,
                description = "Số CMND/CCCD", example = "123456789", position = "B")
    @Column(name = "identity_card", nullable = false, unique = true, length = 20)
    private String identityCard;
    
    @ExcelColumn(name = "First Name", required = true, maxLength = 50, dataType = ExcelColumn.ColumnType.STRING,
                description = "Tên", example = "Nguyễn Văn", position = "C")
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;
    
    @ExcelColumn(name = "Last Name", required = true, maxLength = 50, dataType = ExcelColumn.ColumnType.STRING,
                description = "Họ", example = "A", position = "D")
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;
    
    @ExcelColumn(name = "Email", required = true, maxLength = 100, dataType = ExcelColumn.ColumnType.EMAIL,
                description = "Email", example = "user@example.com", position = "E")
    @Column(name = "email", nullable = false, length = 100)
    private String email;
    
    @ExcelColumn(name = "Phone Number", required = false, maxLength = 20, dataType = ExcelColumn.ColumnType.PHONE,
                description = "Số điện thoại", example = "0123456789", position = "F")
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    
    @ExcelColumn(name = "Birth Date", required = false, dataType = ExcelColumn.ColumnType.DATE,
                pattern = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}",
                description = "Ngày sinh", example = "01/01/1990", position = "G")
    @Column(name = "birth_date")
    private LocalDate birthDate;
    
    @ExcelColumn(name = "Salary", required = false, dataType = ExcelColumn.ColumnType.DECIMAL,
                description = "Lương", example = "5000000", position = "H")
    @Column(name = "salary")
    private Double salary;
    
    @ExcelColumn(name = "Department", required = false, maxLength = 50, dataType = ExcelColumn.ColumnType.STRING,
                description = "Phòng ban", example = "IT", position = "I")
    @Column(name = "department", length = 50)
    private String department;
    
    @ExcelColumn(name = "Created At", required = false, dataType = ExcelColumn.ColumnType.DATE,
                pattern = "\\d{2}/\\d{2}/\\d{4}|\\d{4}-\\d{2}-\\d{2}",
                description = "Ngày tạo", example = "01/01/2024", position = "J")
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    /**
     * Constructor for easy creation with all fields
     */
    public User(String id, String identityCard, String firstName, String lastName, 
                String email, String phoneNumber, LocalDate birthDate, Double salary, 
                String department, LocalDateTime createdAt) {
        this.id = id;
        this.identityCard = identityCard;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.birthDate = birthDate;
        this.salary = salary;
        this.department = department;
        this.createdAt = createdAt;
    }
    
    /**
     * Get full name for display purposes
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }
    
    @Override
    public String toString() {
        return String.format("User{id='%s', identityCard='%s', fullName='%s %s', email='%s'}", 
                           id, identityCard, firstName, lastName, email);
    }
}