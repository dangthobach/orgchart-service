package com.learnmore.infrastructure.repository;

import com.learnmore.infrastructure.persistence.entity.MenuEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MenuRepository extends JpaRepository<MenuEntity, UUID> {
    List<MenuEntity> findByParentIsNull();
    
    // Temporarily disabled - need to implement proper user-menu relationship
    // List<MenuEntity> findRootMenusByUserId(UUID userId);
} 