package com.learnmore.infrastructure.persistence.mapper;

import com.learnmore.domain.common.BaseEntity;
import com.learnmore.infrastructure.persistence.entity.AuditEntity;

public abstract class AbstractMapper<D extends BaseEntity, E extends AuditEntity> {
    protected void setBaseEntityFields(D domain, E entity) {
        domain.setId(entity.getId());
        domain.setDeleted(entity.isDeleted());
        domain.setCreatedAt(entity.getCreatedAt());
        domain.setUpdatedAt(entity.getUpdatedAt());
    }

    protected void setBaseEntityFields(E entity, D domain) {
        entity.setId(domain.getId());
        entity.setDeleted(domain.isDeleted());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
    }
} 