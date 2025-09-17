package com.learnmore.infrastructure.persistence.mapper;


import com.learnmore.domain.user.User;
import com.learnmore.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class UserMapper extends AbstractMapper<User, UserEntity> {

    public User entityToDomain(UserEntity entity) {
        if (entity == null) {
            return null;
        }

        User user = new User();
        setBaseEntityFields(user, entity);
        user.setUsername(entity.getUsername());
        user.setEmail(entity.getEmail());
        user.setPassword(entity.getPassword());
        user.setFullName(entity.getFullName());
        user.setActive(entity.isActive());
        user.assignRoles(entity.getRoles().stream()
                .map(roleEntity -> new RoleMapper().entityToDomain(roleEntity))
                .collect(Collectors.toSet()));
        user.assignTeams(entity.getTeams().stream()
                .map(teamEntity -> new TeamMapper().entityToDomain(teamEntity))
                .collect(Collectors.toSet()));
        return user;
    }

    public UserEntity domainToEntity(User domain) {
        if (domain == null) {
            return null;
        }

        UserEntity entity = new UserEntity();
        setBaseEntityFields(entity, domain);
        entity.setUsername(domain.getUsername());
        entity.setEmail(domain.getEmail());
        entity.setPassword(domain.getPassword());
        entity.setFullName(domain.getFullName());
        entity.setActive(domain.isActive());
        entity.setRoles(domain.getRoles().stream()
                .map(role -> new RoleMapper().domainToEntity(role))
                .collect(Collectors.toSet()));
        entity.setTeams(domain.getTeams().stream()
                .map(team -> new TeamMapper().domainToEntity(team))
                .collect(Collectors.toSet()));
        return entity;
    }
} 