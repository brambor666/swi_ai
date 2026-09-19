package cz.vsb.reservation.infrastructure.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "resource")
public class ResourceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private int capacity;

    // JPA vyžaduje bezparametrický konstruktor.
    protected ResourceJpaEntity() {}

    public ResourceJpaEntity(Long id, String label, int capacity) {
        this.id = id;
        this.label = label;
        this.capacity = capacity;
    }

    public Long getId() { return id; }
    public String getLabel() { return label; }
    public int getCapacity() { return capacity; }
}