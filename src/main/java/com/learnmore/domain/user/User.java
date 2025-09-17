package com.learnmore.domain.user;

import com.learnmore.domain.common.BaseEntity;
import com.learnmore.domain.role.Role;
import com.learnmore.domain.team.Team;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class User extends BaseEntity {
    private final UUID id;
    private String username;
    private String password;
    private String email;
    private String fullName;
    private boolean active;
    private final Set<Role> roles;
    private final Set<Team> teams;

    public User(UUID id, String username, String password, String email, String fullName) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.fullName = fullName;
        this.roles = new HashSet<>();
        this.teams = new HashSet<>();
    }

    public User() {
        this.id = UUID.randomUUID();
        this.roles = new HashSet<>();
        this.teams = new HashSet<>();
        this.active = true; // Default to active
    }

    public void updateProfile(String email, String fullName) {
        this.email = email;
        this.fullName = fullName;
    }

    public void assignRoles(Set<Role> roles) {
        this.roles.addAll(roles);
    }

    public void removeRoles(Set<Role> roles) {
        this.roles.removeAll(roles);
    }

    public void assignTeams(Set<Team> teams) {
        this.teams.addAll(teams);
    }

    public void removeTeams(Set<Team> teams) {
        this.teams.removeAll(teams);
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public Set<Team> getTeams() {
        return teams;
    }
}