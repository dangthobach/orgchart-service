package com.learnmore.config;
import com.learnmore.application.port.input.MenuService;
import com.learnmore.application.service.MenuServiceImpl;
import com.learnmore.application.service.RoleServiceImpl;
import org.springframework.context.annotation.Configuration;

import com.learnmore.application.port.input.RoleService;
import com.learnmore.application.port.output.MenuRepository;
import com.learnmore.application.port.output.RoleRepository;

@Configuration
public class BeanConfiguration {

    public MenuService menuService(MenuRepository menuRepository) {
        return new MenuServiceImpl(menuRepository);
    }

    public RoleService roleService(RoleRepository roleRepository, MenuRepository menuRepository) {
        return new RoleServiceImpl(roleRepository, menuRepository);
    }
}
