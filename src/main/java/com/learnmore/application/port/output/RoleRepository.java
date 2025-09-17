package com.learnmore.application.port.output;

import com.learnmore.domain.role.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {
    Role save(Role role);
    void delete(UUID id);
    Optional<Role> findById(UUID id);
    List<Role> findAll();
    Optional<Role> findByName(String name);
    boolean existsByName(String name);
} 