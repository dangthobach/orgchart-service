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
    @ExcelColumn(name = "ID")
    @Column(name = "id", nullable = false, unique = true)
    private String id;
    
    @ExcelColumn(name = "Identity Card")
    @Column(name = "identity_card", nullable = false, unique = true, length = 20)
    private String identityCard;
    
    @ExcelColumn(name = "First Name")
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;
    
    @ExcelColumn(name = "Last Name")
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;
    
    @ExcelColumn(name = "Email")
    @Column(name = "email", nullable = false, length = 100)
    private String email;
    
    @ExcelColumn(name = "Phone Number")
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    
    @ExcelColumn(name = "Birth Date")
    @Column(name = "birth_date")
    private LocalDate birthDate;
    
    @ExcelColumn(name = "Salary")
    @Column(name = "salary")
    private Double salary;
    
    @ExcelColumn(name = "Department")
    @Column(name = "department", length = 50)
    private String department;
    
    @ExcelColumn(name = "Created At")
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