package cz.vsb.reservation.infrastructure.web;

import cz.vsb.reservation.domain.model.Resource;

public record ResourceResponse(Long id, String label, int capacity) {

    static ResourceResponse from(Resource resource) {
        return new ResourceResponse(resource.getId(), resource.getLabel(), resource.getCapacity());
    }
}