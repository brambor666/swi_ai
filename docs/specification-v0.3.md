# Specification Baseline v0.3 — maximální délka nové rezervace

v0.3 přebírá celou [specifikaci v0.2](specification-v0.2.md) s následující změnou OP-01. Původní v0.2 zůstává historickou baseline. Zdroj: [CR-08 a AC-01–04](intent-and-change.md#c08--cr-08--maximální-délka-nové-rezervace).

## BR-10 — Délka nového návrhu

Při Create musí platit `0 < end − start <= 3 hodiny`, z UTC časů. Limit platí pro všechny uživatele a učebny, včetně prostor vyžadujících schválení. Přesně 3 hodiny jsou povoleny; jakékoli překročení je odmítnuto. Limit je pevný, nemá konfigurační parametr.

## OP-01 — změna kontraktu

Při ostatních platných vstupech délka 1 h nebo přesně 3 h vrátí HTTP 201 a uloží DRAFT se zadanými údaji. Délka nad 3 h vrátí HTTP 409, `code=BUSINESS_RULE_VIOLATION` a zprávu „Maximální délka nové rezervace je 3 hodiny“. Nevznikne záznam ani oznámení.

Nulový/záporný interval nadále vrací 400. Kapacita, vlastnictví, budoucí začátek a další validace z v0.2 platí dál. Priorita chyb u více neplatných vstupů není součástí kontraktu.

## Kompatibilita a rozsah

Existující DRAFT a PENDING_APPROVAL delší než 3 h lze potvrdit/schválit podle pravidel v0.2. Limit není součástí konstruktoru načítajícího staré záznamy ani DB constraintu. Schéma zůstává V1–V3, migrace není potřebná. Aktuální API délku existující rezervace nemění.

Dostupnost, překryvy CONFIRMED, rušení, expirace a oprávnění jsou beze změny. Denní kvóty nejsou zavedené: více samostatných rezervací není tímto pravidlem zakázáno. Delší akce se domlouvají se správcem mimo aplikaci. Přímé administrativní SQL limit BR-10 nevynucuje.

## Ověření

AC-01/02: parametrizovaný `ApprovalWorkflowApiTest.c09_createDurationContract`, skutečné HTTP a samostatné SQL čtení po odpovědi. AC-04: `c09_legacyFourHourReservation_canStillBeAllocated`. Přesná doménová hranice včetně překročení o 1 ns: `ReservationTest.c09_durationBoundaryAndLegacyCompatibility`. Regrese: existující sady Create, lifecycle, approval, persistence a architecture.
