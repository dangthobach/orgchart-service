package com.learnmore.domain.team;

import com.learnmore.domain.api.ApiResource;
import com.learnmore.domain.common.BaseEntity;
import com.learnmore.domain.user.User;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Team extends BaseEntity {
    private String name;
    private String description;
    private Set<User> members = new HashSet<>();
    private User teamLead;
    private Set<ApiResource> accessibleApis = new HashSet<>();

    public void addMember(User user) {
        this.members.add(user);
    }

    public void removeMember(User user) {
        this.members.remove(user);
    }

    public void setTeamLead(User user) {
        if (!this.members.contains(user)) {
            throw new IllegalArgumentException("Team lead must be a member of the team");
        }
        this.teamLead = user;
    }

    public void addApiResource(ApiResource apiResource) {
        this.accessibleApis.add(apiResource);
    }

    public void removeApiResource(ApiResource apiResource) {
        this.accessibleApis.remove(apiResource);
    }

    public boolean canAccessApi(ApiResource apiResource) {
        if (apiResource.isAccessibleByPublic()) {
            return true;
        }
        return this.accessibleApis.contains(apiResource);
    }


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

    public Set<User> getMembers() {
        return members;
    }

    public void setMembers(Set<User> members) {
        this.members = members;
    }

    public User getTeamLead() {
        return teamLead;
    }

    public Set<ApiResource> getAccessibleApis() {
        return accessibleApis;
    }

    public void setAccessibleApis(Set<ApiResource> accessibleApis) {
        this.accessibleApis = accessibleApis;
    }
}