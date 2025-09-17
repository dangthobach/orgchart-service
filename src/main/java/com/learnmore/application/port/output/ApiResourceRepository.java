package com.learnmore.application.port.output;

import com.learnmore.domain.api.ApiResource;

import java.util.UUID;

public interface ApiResourceRepository {

    ApiResource findById(UUID apiResourceId);
}
