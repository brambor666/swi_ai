package cz.vsb.reservation.domain.port.out;

import cz.vsb.reservation.domain.model.Resource;

import java.util.Optional;
import java.util.List;

public interface ResourceRepository {

    Optional<Resource> findById(Long id);
    List<Resource> findAll();
}