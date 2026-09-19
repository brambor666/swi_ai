package cz.vsb.reservation.infrastructure.persistence;

import cz.vsb.reservation.domain.model.Resource;
import cz.vsb.reservation.domain.port.out.ResourceRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class ResourceRepositoryAdapter implements ResourceRepository {

    private final ResourceJpaRepository jpaRepository;

    ResourceRepositoryAdapter(ResourceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Resource> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    private Resource toDomain(ResourceJpaEntity entity) {
        return new Resource(entity.getId(), entity.getLabel(), entity.getCapacity());
    }
}