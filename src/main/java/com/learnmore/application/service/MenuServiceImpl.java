package com.learnmore.application.service;

import com.learnmore.application.port.input.MenuService;
import com.learnmore.application.port.output.MenuRepository;
import lombok.RequiredArgsConstructor;

public class MenuServiceImpl implements MenuService {

    private final MenuRepository menuRepository;

    public MenuServiceImpl(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }
}
