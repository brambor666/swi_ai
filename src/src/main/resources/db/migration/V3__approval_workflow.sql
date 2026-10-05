ALTER TABLE resource ADD COLUMN requires_approval BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE reservation DROP CONSTRAINT reservation_state_check;
ALTER TABLE reservation ADD CONSTRAINT reservation_state_check
    CHECK (state IN ('DRAFT', 'PENDING_APPROVAL', 'CONFIRMED', 'CANCELLED', 'REJECTED', 'EXPIRED'));
-- Přednáškový sál je ukázkový speciální prostor. Existující rezervace zachovávají stav.
UPDATE resource SET requires_approval = TRUE WHERE label = 'Přednáškový sál P1';
