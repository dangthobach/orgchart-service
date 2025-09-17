package com.learnmore.infrastructure.repository;

import com.learnmore.infrastructure.persistence.entity.ApiResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ApiResourceRepository extends JpaRepository<ApiResourceEntity, UUID> {
    // Additional query methods can be defined here if needed
}
