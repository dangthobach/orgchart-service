package com.learnmore.infrastructure.adapter;

import com.learnmore.application.port.output.RoleRepository;
import com.learnmore.domain.role.Role;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RoleAdapter implements RoleRepository {
    @Override
    public Role save(Role role) {
        return null;
    }

    @Override
    public void delete(UUID id) {

    }
    @Override
    public Optional<Role> findById(UUID id) {
        return Optional.empty();
    }

    @Override
    public Optional<Role> findByName(String name) {
        return Optional.empty();
    }

    @Override
    public List<Role> findAll() {
        return null;
    }



    @Override
    public boolean existsByName(String name) {
        return false;
    }
}
