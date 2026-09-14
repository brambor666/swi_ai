package cz.vsb.reservation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ResourceJpaRepository extends JpaRepository<ResourceJpaEntity, Long> {
}