package com.learnmore.infrastructure.adapter;

import com.learnmore.application.port.output.MenuRepository;
import com.learnmore.domain.menu.Menu;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Component
public class MenuAdapter implements MenuRepository {
    @Override
    public Menu save(Menu menu) {
        return null;
    }

    @Override
    public void delete(UUID id) {

    }

    @Override
    public Optional<Menu> findById(UUID id) {
        return Optional.empty();
    }

    @Override
    public List<Menu> findAll() {
        return null;
    }

    @Override
    public List<Menu> findByParentIsNull() {
        return null;
    }

    @Override
    public List<Menu> findRootMenusByUserId(UUID userId) {
        return null;
    }
}
