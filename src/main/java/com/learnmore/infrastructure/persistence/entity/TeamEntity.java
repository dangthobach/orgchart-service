package com.learnmore.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "teams")
@Getter
@Setter
public class TeamEntity extends AuditEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @ManyToMany
    @JoinTable(
        name = "team_members",
        joinColumns = @JoinColumn(name = "team_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<UserEntity> members = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "team_lead_id")
    private UserEntity teamLead;

    @ManyToMany
    @JoinTable(
        name = "team_apis",
        joinColumns = @JoinColumn(name = "team_id"),
        inverseJoinColumns = @JoinColumn(name = "api_id")
    )
    private Set<ApiResourceEntity> accessibleApis = new HashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<UserEntity> getMembers() {
        return members;
    }

    public void setMembers(Set<UserEntity> members) {
        this.members = members;
    }

    public UserEntity getTeamLead() {
        return teamLead;
    }

    public void setTeamLead(UserEntity teamLead) {
        this.teamLead = teamLead;
    }

    public Set<ApiResourceEntity> getAccessibleApis() {
        return accessibleApis;
    }

    public void setAccessibleApis(Set<ApiResourceEntity> accessibleApis) {
        this.accessibleApis = accessibleApis;
    }
}