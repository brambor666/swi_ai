package cz.vsb.reservation.domain.port.out;

import cz.vsb.reservation.domain.model.Resource;

import java.util.Optional;

public interface ResourceRepository {

    Optional<Resource> findById(Long id);
}