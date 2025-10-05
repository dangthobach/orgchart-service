package com.learnmore.application.service;

import com.learnmore.application.dto.UserCreateDTO;
import com.learnmore.application.port.input.UserService;
import com.learnmore.application.port.output.RoleRepository;
import com.learnmore.application.port.output.UserRepository;
import com.learnmore.domain.menu.Menu;
import com.learnmore.domain.role.Role;
import com.learnmore.domain.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserServiceImpl(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public User createUser(UserCreateDTO userDTO) {
        if (userRepository.existsByUsername(userDTO.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User(
            UUID.randomUUID(),
            userDTO.getUsername(),
            userDTO.getPassword(),
            userDTO.getEmail(),
            userDTO.getFullName()
        );

        if (userDTO.getRoleIds() != null && !userDTO.getRoleIds().isEmpty()) {
            Set<Role> roles = userDTO.getRoleIds().stream()
                    .<Role>map(roleId -> roleRepository.findById(UUID.fromString(roleId.toString()))
                            .orElseThrow(() -> new RuntimeException("Role not found: " + roleId)))
                    .collect(Collectors.toSet());
            user.assignRoles(roles);
        }

        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User updateUser(UUID id, UserCreateDTO userDTO) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        existingUser.updateProfile(userDTO.getEmail(), userDTO.getFullName());
        
        if (userDTO.getRoleIds() != null) {
            Set<Role> roles = userDTO.getRoleIds().stream()
                    .<Role>map(roleId -> roleRepository.findById(UUID.fromString(roleId.toString()))
                            .orElseThrow(() -> new RuntimeException("Role not found: " + roleId)))
                    .collect(Collectors.toSet());
            existingUser.assignRoles(roles);
        }
        
        return userRepository.save(existingUser);
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        userRepository.delete(id);
    }

    @Override
    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public User assignRolesToUser(UUID userId, Set<UUID> roleIds) {
        User user = getUserById(userId);
        Set<Role> roles = roleIds.stream()
                .<Role>map(roleId -> roleRepository.findById(roleId)
                        .orElseThrow(() -> new RuntimeException("Role not found: " + roleId)))
                .collect(Collectors.toSet());
        user.assignRoles(roles);
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public User removeRolesFromUser(UUID userId, Set<UUID> roleIds) {
        User user = getUserById(userId);
        Set<Role> rolesToRemove = roleIds.stream()
                .<Role>map(roleId -> roleRepository.findById(roleId)
                        .orElseThrow(() -> new RuntimeException("Role not found: " + roleId)))
                .collect(Collectors.toSet());
        user.removeRoles(rolesToRemove);
        return userRepository.save(user);
    }

    @Override
    public List<Menu> getMenuTreeByUserId(UUID userId) {
        User user = getUserById(userId);
        return user.getRoles().stream()
                .flatMap(role -> role.getMenus().stream())
                .distinct()
                .collect(Collectors.toList());
    }
} 