-- Potřebujeme btree_gist pro EXCLUDE constraint proti překryvu rezervací (viz ADR-002)
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE resource (
                          id          BIGSERIAL PRIMARY KEY,
                          label       VARCHAR(255) NOT NULL,
                          capacity    INTEGER NOT NULL CHECK (capacity > 0)
);

CREATE TABLE reservation (
                             id              BIGSERIAL PRIMARY KEY,
                             resource_id     BIGINT NOT NULL REFERENCES resource(id),
                             user_id         VARCHAR(255) NOT NULL,
                             start_time      TIMESTAMP NOT NULL,
                             end_time        TIMESTAMP NOT NULL,
                             participant_count INTEGER NOT NULL CHECK (participant_count > 0),
                             state           VARCHAR(20) NOT NULL CHECK (state IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),

                             CHECK (end_time > start_time)
);

-- Databázová pojistka proti souběhu (viz ADR-002):
-- dvě CONFIRMED rezervace stejné učebny se nesmí časově překrývat.
-- DRAFT a CANCELLED rezervace tímto constraintem omezené nejsou.
ALTER TABLE reservation
    ADD CONSTRAINT no_overlapping_confirmed_reservations
    EXCLUDE USING gist (
        resource_id WITH =,
        tsrange(start_time, end_time) WITH &&
    )
    WHERE (state = 'CONFIRMED');

CREATE INDEX idx_reservation_resource ON reservation(resource_id);
CREATE INDEX idx_reservation_state ON reservation(state);