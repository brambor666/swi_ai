package cz.vsb.reservation.domain.model;

import java.util.Objects;

/**
 * Učebna — čistý doménový model, žádná závislost na Springu ani JPA.
 * ID je nullable, protože nová (dosud neuložená) instance ho ještě nemá.
 */
public final class Resource {

    private final Long id;
    private final String label;
    private final int capacity;

    public Resource(Long id, String label, int capacity) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Label nesmí být prázdný");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Kapacita musí být kladné číslo");
        }
        this.id = id;
        this.label = label;
        this.capacity = capacity;
    }

    /** Tovární metoda pro novou učebnu, která ještě nemá přidělené ID z databáze. */
    public static Resource createNew(String label, int capacity) {
        return new Resource(null, label, capacity);
    }

    /**
     * Business pravidlo z README/intent-and-change.md:
     * počet účastníků rezervace nesmí překročit kapacitu učebny.
     */
    public boolean canAccommodate(int participantCount) {
        return participantCount <= capacity;
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Resource other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}