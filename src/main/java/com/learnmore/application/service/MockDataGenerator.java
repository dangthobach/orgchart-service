package com.learnmore.application.service;

import com.learnmore.application.dto.User;
import com.learnmore.application.dto.Role;
import com.learnmore.application.dto.Permission;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock data generator service using Java Faker
 * Generates large datasets with guaranteed unique constraints
 */
@Service
public class MockDataGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(MockDataGenerator.class);
    
    private final Faker faker;
    private final Set<String> usedIds = new HashSet<>();
    private final Set<String> usedIdentityCards = new HashSet<>();
    
    // Predefined departments for realistic data
    private final String[] departments = {
        "Engineering", "Sales", "Marketing", "HR", "Finance", 
        "Operations", "Legal", "IT", "Customer Service", "Research"
    };
    
    // Predefined role names for realistic data
    private final String[] roleNames = {
        "Admin", "Manager", "Developer", "Analyst", "Designer", 
        "Tester", "Support", "Sales", "Marketing", "HR", 
        "Finance", "Operations", "Legal", "Customer Service", "Research",
        "Senior Developer", "Lead Developer", "Project Manager", "Product Manager",
        "Business Analyst", "Data Analyst", "UX Designer", "UI Designer",
        "DevOps Engineer", "System Administrator", "Database Administrator",
        "Security Specialist", "Quality Assurance", "Technical Writer", "Consultant"
    };
    
    // Predefined permission resources
    private final String[] permissionResources = {
        "User", "Role", "Permission", "Department", "Project", "Task", "Report",
        "Dashboard", "Settings", "Profile", "Document", "File", "Notification",
        "Audit", "Log", "System", "Configuration", "Backup", "Restore", "Export"
    };
    
    public MockDataGenerator() {
        this.faker = new Faker();
    }
    
    /**
     * Generate specified number of unique User records
     * Ensures ID and IdentityCard uniqueness across all records
     */
    public List<User> generateUsers(int count) {
        logger.info("🚀 Starting generation of {} User records with Java Faker", count);
        long startTime = System.currentTimeMillis();
        
        List<User> users = new ArrayList<>(count);
        
        // Clear previous unique sets if reusing generator
        usedIds.clear();
        usedIdentityCards.clear();
        
        int batchSize = 10000;
        int processedCount = 0;
        
        for (int i = 0; i < count; i++) {
            User user = generateUniqueUser();
            users.add(user);
            processedCount++;
            
            // Progress logging for large datasets
            if (processedCount % batchSize == 0) {
                double progressPercent = (processedCount * 100.0) / count;
                long elapsedTime = System.currentTimeMillis() - startTime;
                double recordsPerSecond = processedCount * 1000.0 / elapsedTime;
                
                logger.info("📊 Progress: {}/{} ({:.1f}%) - {:.0f} records/sec", 
                           processedCount, count, progressPercent, recordsPerSecond);
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        double recordsPerSecond = count * 1000.0 / totalTime;
        
        logger.info("✅ Successfully generated {} User records in {}ms ({:.0f} records/sec)", 
                   count, totalTime, recordsPerSecond);
        logger.info("📈 Unique constraints: {} IDs, {} Identity Cards", 
                   usedIds.size(), usedIdentityCards.size());
        
        return users;
    }
    
    /**
     * Generate a single User with guaranteed unique constraints
     */
    private User generateUniqueUser() {
        String id = generateUniqueId();
        String identityCard = generateUniqueIdentityCard();
        
        User user = new User();
        user.setId(id);
        user.setIdentityCard(identityCard);
        user.setFirstName(faker.name().firstName());
        user.setLastName(faker.name().lastName());
        user.setEmail(generateRealisticemail(user.getFirstName(), user.getLastName()));
        user.setPhoneNumber(faker.phoneNumber().cellPhone());
        user.setBirthDate(generateRealisticBirthDate());
        user.setSalary(generateRealisticSalary());
        user.setDepartment(departments[ThreadLocalRandom.current().nextInt(departments.length)]);
        user.setCreatedAt(generateRealisticCreatedAt());
        
        return user;
    }
    
    /**
     * Generate unique ID in format: USR-YYYYMMDD-NNNNNNNN
     */
    private String generateUniqueId() {
        String id;
        int attempts = 0;
        do {
            String datePrefix = LocalDate.now().toString().replace("-", "");
            String uniqueNumber = String.format("%08d", ThreadLocalRandom.current().nextInt(100000000));
            id = "USR-" + datePrefix + "-" + uniqueNumber;
            attempts++;
            
            if (attempts > 1000) {
                // Fallback to UUID if having trouble generating unique IDs
                id = "USR-" + UUID.randomUUID().toString().substring(0, 16).toUpperCase();
            }
        } while (usedIds.contains(id));
        
        usedIds.add(id);
        return id;
    }
    
    /**
     * Generate unique Identity Card number (Vietnamese format: 12 digits)
     */
    private String generateUniqueIdentityCard() {
        String identityCard;
        int attempts = 0;
        do {
            // Vietnamese identity card: 12 digits
            StringBuilder sb = new StringBuilder(12);
            for (int i = 0; i < 12; i++) {
                sb.append(ThreadLocalRandom.current().nextInt(10));
            }
            identityCard = sb.toString();
            attempts++;
            
            if (attempts > 1000) {
                // Fallback with timestamp if having trouble
                identityCard = String.valueOf(System.nanoTime()).substring(7, 19);
            }
        } while (usedIdentityCards.contains(identityCard));
        
        usedIdentityCards.add(identityCard);
        return identityCard;
    }
    
    /**
     * Generate realistic email based on name
     */
    private String generateRealisticemail(String firstName, String lastName) {
        String[] domains = {"gmail.com", "yahoo.com", "outlook.com", "company.com", "email.com"};
        String domain = domains[ThreadLocalRandom.current().nextInt(domains.length)];
        
        String emailPrefix = (firstName + "." + lastName).toLowerCase()
                .replaceAll("[^a-z]", "");
        
        // Add random number to avoid collisions
        int randomNum = ThreadLocalRandom.current().nextInt(1000);
        
        return emailPrefix + randomNum + "@" + domain;
    }
    
    /**
     * Generate realistic birth date (18-65 years old)
     */
    private LocalDate generateRealisticBirthDate() {
        LocalDate now = LocalDate.now();
        LocalDate minDate = now.minusYears(65);  // 65 years old maximum
        LocalDate maxDate = now.minusYears(18);  // 18 years old minimum
        
        long minDay = minDate.toEpochDay();
        long maxDay = maxDate.toEpochDay();
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
        
        return LocalDate.ofEpochDay(randomDay);
    }
    
    /**
     * Generate realistic salary (20M - 200M VND)
     */
    private Double generateRealisticSalary() {
        // Salary range: 20,000,000 - 200,000,000 VND
        double minSalary = 20_000_000.0;
        double maxSalary = 200_000_000.0;
        
        double salary = minSalary + (ThreadLocalRandom.current().nextDouble() * (maxSalary - minSalary));
        
        // Round to nearest 100,000
        return Math.round(salary / 100_000.0) * 100_000.0;
    }
    
    /**
     * Generate realistic created timestamp (within last 2 years)
     */
    private LocalDateTime generateRealisticCreatedAt() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twoYearsAgo = now.minusYears(2);
        
        long minSecond = twoYearsAgo.atZone(ZoneId.systemDefault()).toEpochSecond();
        long maxSecond = now.atZone(ZoneId.systemDefault()).toEpochSecond();
        long randomSecond = ThreadLocalRandom.current().nextLong(minSecond, maxSecond);
        
        return LocalDateTime.ofEpochSecond(randomSecond, 0, ZoneId.systemDefault().getRules().getOffset(now));
    }
    
    /**
     * Generate users in batches for memory efficiency
     */
    public List<List<User>> generateUsersInBatches(int totalCount, int batchSize) {
        logger.info("🔄 Generating {} users in batches of {}", totalCount, batchSize);
        
        List<List<User>> batches = new ArrayList<>();
        int remainingCount = totalCount;
        int batchNumber = 1;
        
        while (remainingCount > 0) {
            int currentBatchSize = Math.min(batchSize, remainingCount);
            
            logger.info("📦 Generating batch {} with {} records", batchNumber, currentBatchSize);
            List<User> batch = generateUsers(currentBatchSize);
            batches.add(batch);
            
            remainingCount -= currentBatchSize;
            batchNumber++;
        }
        
        logger.info("✅ Generated {} batches with total {} users", batches.size(), totalCount);
        return batches;
    }
    
    /**
     * Get generation statistics
     */
    public Map<String, Object> getGenerationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("uniqueIds", usedIds.size());
        stats.put("uniqueIdentityCards", usedIdentityCards.size());
        stats.put("departments", Arrays.asList(departments));
        
        return stats;
    }
    
    /**
     * Generate specified number of unique Role records
     * Ensures ID and Name uniqueness across all records
     */
    public List<Role> generateRoles(int count) {
        logger.info("🚀 Starting generation of {} Role records with Java Faker", count);
        long startTime = System.currentTimeMillis();
        
        List<Role> roles = new ArrayList<>(count);
        Set<String> usedRoleNames = new HashSet<>();
        
        for (int i = 0; i < count; i++) {
            Role role = generateUniqueRole(usedRoleNames);
            roles.add(role);
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        double recordsPerSecond = count * 1000.0 / totalTime;
        
        logger.info("✅ Successfully generated {} Role records in {}ms ({:.0f} records/sec)", 
                   count, totalTime, recordsPerSecond);
        
        return roles;
    }
    
    /**
     * Generate a single Role with guaranteed unique constraints
     */
    private Role generateUniqueRole(Set<String> usedRoleNames) {
        String roleName;
        int attempts = 0;
        
        do {
            if (attempts < roleNames.length) {
                // Use predefined role names first
                roleName = roleNames[attempts];
            } else {
                // Generate random role names
                roleName = faker.job().title() + " " + faker.job().seniority();
            }
            attempts++;
            
            if (attempts > 1000) {
                // Fallback to UUID if having trouble generating unique names
                roleName = "ROLE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }
        } while (usedRoleNames.contains(roleName));
        
        usedRoleNames.add(roleName);
        
        String id = "ROLE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String description = faker.lorem().sentence(10);
        Boolean isActive = ThreadLocalRandom.current().nextBoolean();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = now.minusDays(ThreadLocalRandom.current().nextInt(365));
        LocalDateTime updatedAt = createdAt.plusDays(ThreadLocalRandom.current().nextInt(30));
        
        return new Role(id, roleName, description, isActive, createdAt, updatedAt);
    }
    
    /**
     * Generate specified number of unique Permission records
     * Ensures ID and Code uniqueness across all records
     */
    public List<Permission> generatePermissions(int count) {
        logger.info("🚀 Starting generation of {} Permission records with Java Faker", count);
        long startTime = System.currentTimeMillis();
        
        List<Permission> permissions = new ArrayList<>(count);
        Set<String> usedPermissionCodes = new HashSet<>();
        
        for (int i = 0; i < count; i++) {
            Permission permission = generateUniquePermission(usedPermissionCodes);
            permissions.add(permission);
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        double recordsPerSecond = count * 1000.0 / totalTime;
        
        logger.info("✅ Successfully generated {} Permission records in {}ms ({:.0f} records/sec)", 
                   count, totalTime, recordsPerSecond);
        
        return permissions;
    }
    
    /**
     * Generate a single Permission with guaranteed unique constraints
     */
    private Permission generateUniquePermission(Set<String> usedPermissionCodes) {
        String permissionCode;
        int attempts = 0;
        
        do {
            String resource = permissionResources[ThreadLocalRandom.current().nextInt(permissionResources.length)];
            Permission.PermissionType type = Permission.PermissionType.values()[
                ThreadLocalRandom.current().nextInt(Permission.PermissionType.values().length)
            ];
            permissionCode = type.getValue() + "_" + resource.toUpperCase();
            attempts++;
            
            if (attempts > 1000) {
                // Fallback to UUID if having trouble generating unique codes
                permissionCode = "PERM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }
        } while (usedPermissionCodes.contains(permissionCode));
        
        usedPermissionCodes.add(permissionCode);
        
        String id = "PERM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String resource = permissionResources[ThreadLocalRandom.current().nextInt(permissionResources.length)];
        Permission.PermissionType type = Permission.PermissionType.values()[
            ThreadLocalRandom.current().nextInt(Permission.PermissionType.values().length)
        ];
        String name = type.getValue() + " " + resource;
        String description = faker.lorem().sentence(8);
        Boolean isActive = ThreadLocalRandom.current().nextBoolean();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = now.minusDays(ThreadLocalRandom.current().nextInt(365));
        LocalDateTime updatedAt = createdAt.plusDays(ThreadLocalRandom.current().nextInt(30));
        
        return new Permission(id, name, permissionCode, description, type.getValue(), resource, isActive, createdAt, updatedAt);
    }
    
    /**
     * Generate all three entity types for multi-sheet Excel export
     */
    public Map<String, List<?>> generateAllEntities(int userCount, int roleCount, int permissionCount) {
        logger.info("🎯 Generating complete dataset: {} Users, {} Roles, {} Permissions", 
                   userCount, roleCount, permissionCount);
        
        Map<String, List<?>> allData = new HashMap<>();
        
        // Generate Users
        allData.put("User", generateUsers(userCount));
        
        // Generate Roles
        allData.put("Role", generateRoles(roleCount));
        
        // Generate Permissions
        allData.put("Permission", generatePermissions(permissionCount));
        
        logger.info("✅ Complete dataset generated successfully");
        return allData;
    }
    
    /**
     * Clear internal caches for memory efficiency
     */
    public void clearCaches() {
        usedIds.clear();
        usedIdentityCards.clear();
        logger.info("🧹 Cleared MockDataGenerator caches");
    }
}