package com.learnmore.application.port.input;

import com.learnmore.application.dto.UserCreateDTO;
import com.learnmore.domain.user.User;
import com.learnmore.domain.menu.Menu;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface UserService {
    User createUser(UserCreateDTO userDTO);
    User updateUser(UUID id, UserCreateDTO userDTO);
    void deleteUser(UUID id);
    User getUserById(UUID id);
    List<User> getAllUsers();
    User assignRolesToUser(UUID userId, Set<UUID> roleIds);
    User removeRolesFromUser(UUID userId, Set<UUID> roleIds);
    List<Menu> getMenuTreeByUserId(UUID userId);
} 