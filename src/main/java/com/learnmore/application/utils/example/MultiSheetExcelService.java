package com.learnmore.application.utils.example;

import com.learnmore.application.utils.ExcelUtil;
import com.learnmore.application.utils.ExcelColumn;
import com.learnmore.application.utils.config.ExcelConfig;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;

/**
 * Service example demonstrating multi-sheet Excel processing
 * Practical implementation for organizational data import
 */
@Service
public class MultiSheetExcelService {
    
    /**
     * Import organizational data from multi-sheet Excel file
     * Each sheet contains different entity types (Users, Roles, Teams, etc.)
     */
    public OrganizationImportResult importOrganizationData(InputStream excelFile) throws Exception {
        
        // Configure Excel processing
        ExcelConfig config = ExcelConfig.builder()
                .batchSize(1000)
                .memoryThreshold(500)
                .enableProgressTracking(true)
                .enableMemoryMonitoring(true)
                .strictValidation(true)
                .failOnFirstError(false)
                .requiredFields("id", "name") // Common required fields
                .build();
        
        // Define sheet-class mapping
        Map<String, Class<?>> sheetMapping = new HashMap<>();
        sheetMapping.put("Users", UserImportData.class);
        sheetMapping.put("Roles", RoleImportData.class);
        sheetMapping.put("Teams", TeamImportData.class);
        sheetMapping.put("Permissions", PermissionImportData.class);
        
        // Process all sheets
        Map<String, ExcelUtil.MultiSheetResult> results = 
            ExcelUtil.processMultiSheetExcel(excelFile, sheetMapping, config);
        
        // Transform results to business objects
        return transformToBusinessObjects(results);
    }
    
    /**
     * Import large organizational data using streaming approach
     * Suitable for files with hundreds of thousands of records
     */
    public void importOrganizationDataStreaming(InputStream excelFile, 
                                               OrganizationDataProcessor dataProcessor) throws Exception {
        
        ExcelConfig config = ExcelConfig.builder()
                .batchSize(500) // Smaller batches for streaming
                .memoryThreshold(300)
                .enableProgressTracking(true)
                .enableMemoryMonitoring(true)
                .parallelProcessing(true)
                .build();
        
        // Define streaming processors
        Map<String, ExcelUtil.SheetProcessorConfig> sheetProcessors = new HashMap<>();
        
        // User data streaming processor
        Consumer<List<UserImportData>> userProcessor = batch -> {
            try {
                dataProcessor.processUsers(batch);
                System.out.println("Processed " + batch.size() + " users");
            } catch (Exception e) {
                System.err.println("Error processing user batch: " + e.getMessage());
            }
        };
        sheetProcessors.put("Users", new ExcelUtil.SheetProcessorConfig(UserImportData.class, userProcessor));
        
        // Role data streaming processor
        Consumer<List<RoleImportData>> roleProcessor = batch -> {
            try {
                dataProcessor.processRoles(batch);
                System.out.println("Processed " + batch.size() + " roles");
            } catch (Exception e) {
                System.err.println("Error processing role batch: " + e.getMessage());
            }
        };
        sheetProcessors.put("Roles", new ExcelUtil.SheetProcessorConfig(RoleImportData.class, roleProcessor));
        
        // Team data streaming processor
        Consumer<List<TeamImportData>> teamProcessor = batch -> {
            try {
                dataProcessor.processTeams(batch);
                System.out.println("Processed " + batch.size() + " teams");
            } catch (Exception e) {
                System.err.println("Error processing team batch: " + e.getMessage());
            }
        };
        sheetProcessors.put("Teams", new ExcelUtil.SheetProcessorConfig(TeamImportData.class, teamProcessor));
        
        // Permission data streaming processor
        Consumer<List<PermissionImportData>> permissionProcessor = batch -> {
            try {
                dataProcessor.processPermissions(batch);
                System.out.println("Processed " + batch.size() + " permissions");
            } catch (Exception e) {
                System.err.println("Error processing permission batch: " + e.getMessage());
            }
        };
        sheetProcessors.put("Permissions", new ExcelUtil.SheetProcessorConfig(PermissionImportData.class, permissionProcessor));
        
        // Execute streaming processing
        ExcelUtil.processMultiSheetExcelStreaming(excelFile, sheetProcessors, config);
    }
    
    /**
     * Transform Excel results to business objects
     */
    private OrganizationImportResult transformToBusinessObjects(Map<String, ExcelUtil.MultiSheetResult> results) {
        
        OrganizationImportResult importResult = new OrganizationImportResult();
        
        // Process Users
        if (results.containsKey("Users")) {
            ExcelUtil.MultiSheetResult userResult = results.get("Users");
            if (userResult.isSuccessful()) {
                @SuppressWarnings("unchecked")
                List<UserImportData> users = (List<UserImportData>) userResult.getData();
                importResult.setUsers(users);
                importResult.setUserImportSuccess(true);
            } else {
                importResult.setUserErrors(userResult.getErrors());
                importResult.setUserImportSuccess(false);
            }
        }
        
        // Process Roles
        if (results.containsKey("Roles")) {
            ExcelUtil.MultiSheetResult roleResult = results.get("Roles");
            if (roleResult.isSuccessful()) {
                @SuppressWarnings("unchecked")
                List<RoleImportData> roles = (List<RoleImportData>) roleResult.getData();
                importResult.setRoles(roles);
                importResult.setRoleImportSuccess(true);
            } else {
                importResult.setRoleErrors(roleResult.getErrors());
                importResult.setRoleImportSuccess(false);
            }
        }
        
        // Process Teams
        if (results.containsKey("Teams")) {
            ExcelUtil.MultiSheetResult teamResult = results.get("Teams");
            if (teamResult.isSuccessful()) {
                @SuppressWarnings("unchecked")
                List<TeamImportData> teams = (List<TeamImportData>) teamResult.getData();
                importResult.setTeams(teams);
                importResult.setTeamImportSuccess(true);
            } else {
                importResult.setTeamErrors(teamResult.getErrors());
                importResult.setTeamImportSuccess(false);
            }
        }
        
        // Process Permissions
        if (results.containsKey("Permissions")) {
            ExcelUtil.MultiSheetResult permissionResult = results.get("Permissions");
            if (permissionResult.isSuccessful()) {
                @SuppressWarnings("unchecked")
                List<PermissionImportData> permissions = (List<PermissionImportData>) permissionResult.getData();
                importResult.setPermissions(permissions);
                importResult.setPermissionImportSuccess(true);
            } else {
                importResult.setPermissionErrors(permissionResult.getErrors());
                importResult.setPermissionImportSuccess(false);
            }
        }
        
        return importResult;
    }
    
    /**
     * Result object for organization import
     */
    public static class OrganizationImportResult {
        private List<UserImportData> users = new ArrayList<>();
        private List<RoleImportData> roles = new ArrayList<>();
        private List<TeamImportData> teams = new ArrayList<>();
        private List<PermissionImportData> permissions = new ArrayList<>();
        
        private boolean userImportSuccess = false;
        private boolean roleImportSuccess = false;
        private boolean teamImportSuccess = false;
        private boolean permissionImportSuccess = false;
        
        private List<String> userErrors = new ArrayList<>();
        private List<String> roleErrors = new ArrayList<>();
        private List<String> teamErrors = new ArrayList<>();
        private List<String> permissionErrors = new ArrayList<>();
        
        // Getters and setters
        public List<UserImportData> getUsers() { return users; }
        public void setUsers(List<UserImportData> users) { this.users = users; }
        
        public List<RoleImportData> getRoles() { return roles; }
        public void setRoles(List<RoleImportData> roles) { this.roles = roles; }
        
        public List<TeamImportData> getTeams() { return teams; }
        public void setTeams(List<TeamImportData> teams) { this.teams = teams; }
        
        public List<PermissionImportData> getPermissions() { return permissions; }
        public void setPermissions(List<PermissionImportData> permissions) { this.permissions = permissions; }
        
        public boolean isUserImportSuccess() { return userImportSuccess; }
        public void setUserImportSuccess(boolean userImportSuccess) { this.userImportSuccess = userImportSuccess; }
        
        public boolean isRoleImportSuccess() { return roleImportSuccess; }
        public void setRoleImportSuccess(boolean roleImportSuccess) { this.roleImportSuccess = roleImportSuccess; }
        
        public boolean isTeamImportSuccess() { return teamImportSuccess; }
        public void setTeamImportSuccess(boolean teamImportSuccess) { this.teamImportSuccess = teamImportSuccess; }
        
        public boolean isPermissionImportSuccess() { return permissionImportSuccess; }
        public void setPermissionImportSuccess(boolean permissionImportSuccess) { this.permissionImportSuccess = permissionImportSuccess; }
        
        public List<String> getUserErrors() { return userErrors; }
        public void setUserErrors(List<String> userErrors) { this.userErrors = userErrors; }
        
        public List<String> getRoleErrors() { return roleErrors; }
        public void setRoleErrors(List<String> roleErrors) { this.roleErrors = roleErrors; }
        
        public List<String> getTeamErrors() { return teamErrors; }
        public void setTeamErrors(List<String> teamErrors) { this.teamErrors = teamErrors; }
        
        public List<String> getPermissionErrors() { return permissionErrors; }
        public void setPermissionErrors(List<String> permissionErrors) { this.permissionErrors = permissionErrors; }
        
        public boolean hasAnyErrors() {
            return !userErrors.isEmpty() || !roleErrors.isEmpty() || 
                   !teamErrors.isEmpty() || !permissionErrors.isEmpty();
        }
        
        public boolean isCompleteSuccess() {
            return userImportSuccess && roleImportSuccess && 
                   teamImportSuccess && permissionImportSuccess && !hasAnyErrors();
        }
        
        public String getSummary() {
            return String.format("Import Summary - Users: %d (Success: %b), Roles: %d (Success: %b), " +
                               "Teams: %d (Success: %b), Permissions: %d (Success: %b), Total Errors: %d",
                               users.size(), userImportSuccess,
                               roles.size(), roleImportSuccess,
                               teams.size(), teamImportSuccess,
                               permissions.size(), permissionImportSuccess,
                               userErrors.size() + roleErrors.size() + teamErrors.size() + permissionErrors.size());
        }
    }
    
    /**
     * Interface for processing imported data
     */
    public interface OrganizationDataProcessor {
        void processUsers(List<UserImportData> users) throws Exception;
        void processRoles(List<RoleImportData> roles) throws Exception;
        void processTeams(List<TeamImportData> teams) throws Exception;
        void processPermissions(List<PermissionImportData> permissions) throws Exception;
    }
    
    // Import data classes would be defined here or in separate files
    // These are simplified examples
    
    public static class UserImportData {
        @ExcelColumn(name = "User ID")
        private String id;
        
        @ExcelColumn(name = "Full Name")
        private String name;
        
        @ExcelColumn(name = "Email")
        private String email;
        
        @ExcelColumn(name = "Department")
        private String department;
        
        @ExcelColumn(name = "Manager ID")
        private String managerId;
        
        @ExcelColumn(name = "Active")
        private Boolean active;
        
        // Constructors, getters, setters...
        public UserImportData() {}
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        
        public String getManagerId() { return managerId; }
        public void setManagerId(String managerId) { this.managerId = managerId; }
        
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }
    
    public static class RoleImportData {
        @ExcelColumn(name = "Role ID")
        private String id;
        
        @ExcelColumn(name = "Role Name")
        private String name;
        
        @ExcelColumn(name = "Description")
        private String description;
        
        @ExcelColumn(name = "Level")
        private Integer level;
        
        // Constructors, getters, setters...
        public RoleImportData() {}
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
    }
    
    public static class TeamImportData {
        @ExcelColumn(name = "Team ID")
        private String id;
        
        @ExcelColumn(name = "Team Name")
        private String name;
        
        @ExcelColumn(name = "Team Lead")
        private String teamLead;
        
        @ExcelColumn(name = "Department")
        private String department;
        
        // Constructors, getters, setters...
        public TeamImportData() {}
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getTeamLead() { return teamLead; }
        public void setTeamLead(String teamLead) { this.teamLead = teamLead; }
        
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
    }
    
    public static class PermissionImportData {
        @ExcelColumn(name = "Permission ID")
        private String id;
        
        @ExcelColumn(name = "Permission Name")
        private String name;
        
        @ExcelColumn(name = "Resource")
        private String resource;
        
        @ExcelColumn(name = "Action")
        private String action;
        
        // Constructors, getters, setters...
        public PermissionImportData() {}
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getResource() { return resource; }
        public void setResource(String resource) { this.resource = resource; }
        
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }
}