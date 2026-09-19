package cz.vsb.reservation.domain;

import cz.vsb.reservation.domain.model.Resource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceTest {

    @Test
    void canAccommodate_returnsTrue_whenParticipantsFitCapacity() {
        Resource resource = Resource.createNew("Učebna A1", 30);

        assertTrue(resource.canAccommodate(30));
        assertTrue(resource.canAccommodate(15));
    }

    @Test
    void canAccommodate_returnsFalse_whenParticipantsExceedCapacity() {
        Resource resource = Resource.createNew("Učebna A1", 30);

        assertFalse(resource.canAccommodate(31));
    }

    @Test
    void constructor_throws_whenCapacityIsZeroOrNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> Resource.createNew("Učebna A1", 0));
        assertThrows(IllegalArgumentException.class,
                () -> Resource.createNew("Učebna A1", -5));
    }

    @Test
    void constructor_throws_whenLabelIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> Resource.createNew("", 10));
        assertThrows(IllegalArgumentException.class,
                () -> Resource.createNew("   ", 10));
    }
}