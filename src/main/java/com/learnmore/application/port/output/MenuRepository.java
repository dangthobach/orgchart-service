package com.learnmore.application.port.output;

import com.learnmore.domain.menu.Menu;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuRepository {
    Menu save(Menu menu);
    void delete(UUID id);
    Optional<Menu> findById(UUID id);
    List<Menu> findAll();
    List<Menu> findByParentIsNull();
    List<Menu> findRootMenusByUserId(UUID userId);
}
