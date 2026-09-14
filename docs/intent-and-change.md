# Project Frame

## Reservation domain
Učebny (vzdělávací, přednáškové nebo zasedací prostory).

## Purpose
Systém slouží studentům a vyučujícím k efektivní rezervaci sdílených školních prostor. Cílem je zabránit konfliktům v čase ("double-booking") a zajistit, že vybraná učebna kapacitně odpovídá plánované události.

## Users / Stakeholders
* Student
* Vyučující
* (Správce budovy)

## Core concepts
* **Reservation** (Rezervace)
* **Resource** (Učebna)
* **User** (Uživatel - žadatel)

## Core operations
* Create reservation
* Confirm / approve reservation
* Cancel reservation
* Check availability

## Persistent state
* **Resource:** ID, označení (název), maximální kapacita.
* **Reservation:** ID, identifikátor učebny (Resource ID), identifikátor uživatele (User ID), časový interval (začátek a konec), počet účastníků, aktuální stav.

## State-changing operation
* **Potvrzení:** `DRAFT` → `CONFIRMED`
* **Zrušení:** `DRAFT` nebo `CONFIRMED` → `CANCELLED`

## Common business rule
Confirmed reservations for the same resource must not overlap.

## Domain-specific business rule
Počet účastníků rezervace nesmí překročit maximální kapacitu dané učebny.

## External / system boundary
Notification Service (zajišťuje asynchronní odesílání upozornění uživatelům při potvrzení nebo zrušení rezervace).

## Assumption
Předpokládáme, že autentizaci uživatelů kompletně řeší externí systém (Identity Provider) a naše aplikace pracuje pouze s ověřenými uživatelskými ID.

## Unknown
Zatím není jisté, zda a jak se budou do systému synchronizovat pevné rozvrhy výuky z univerzitního informačního systému (zda půjdou naimportovat jako série klasických rezervací, nebo budou mít speciální datový model).

## Selected future pressure
Category: C — Changeability

Concrete pressure:
V budoucnu může škola zavést pravidlo, že potvrzenou rezervaci
lze zrušit nejpozději 2 hodiny před jejím začátkem.

Why it is relevant to our reservation system:
Rušení rezervací na poslední chvíli ztěžuje využití učebny
ostatními uživateli. Systém by proto musel při rušení potvrzené
rezervace ověřit, že do jejího začátku zbývají alespoň 2 hodiny.

xxx