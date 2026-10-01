# Rezervační systém učeben
Swi profi projekt

https://github.com/brambor666/swi_ai

Kryštof Lalošák
Filip Vodák
Jakub Juchelka

## Co rezervujeme

Náš systém umožňuje rezervaci učeben.

| Povinný prvek | Popis |
|---|---|
| Resource | Učebna — ID, označení, kapacita |
| Reservation | Rezervace — ID, učebna, uživatel, začátek, konec, počet účastníků, stav |
| User | Student nebo vyučující, který rezervaci vytváří |
| States | DRAFT — návrh, CONFIRMED — potvrzená, CANCELLED — zrušená |
| Operations | Vytvořit rezervaci, potvrdit rezervaci, zrušit rezervaci, ověřit dostupnost učebny |
| Common rule | Dvě potvrzené rezervace stejné učebny se nesmějí časově překrývat |
| Boundary | Notification Service — odesílá uživateli oznámení o potvrzení nebo zrušení rezervace |

### Vlastní business rule

Počet účastníků rezervace nesmí překročit kapacitu učebny.

Podle [specifikace v0.1](docs/specification-v0.1.md) se kapacita kontroluje již při vytvoření. Časy jsou v UTC, frontend je převádí. Vytvoření a potvrzení jsou povoleny pouze před začátkem. Zrušení potvrzené rezervace je povoleno nejméně 2 hodiny před začátkem včetně přesné hranice; zrušení návrhu a dotaz na dostupnost nemají časové omezení. Rozhoduje čas serveru při provádění změny. Uživatel vytváří a ruší pouze vlastní rezervace; o potvrzení vlastního návrhu žádá systém, který rozhoduje podle dostupnosti a pravidel. Dostupnost může zjišťovat každý ověřený uživatel. Selhání notifikace nemění výsledek operace a oznámení se zahodí.

## CP1 walking skeleton

POST /reservations → validace → uložení do PostgreSQL
→ vrácení ID rezervace → automatizovaná kontrola.

Požadavek obsahuje ID existující učebny, ID uživatele, začátek,
konec a počet účastníků. Aplikace ověří existenci učebny,
neprázdné ID uživatele, vyplněný časový interval, konec
po začátku a kladný počet účastníků. Validace zahrnuje také horní mez kapacity, shodu vlastníka s ověřeným uživatelem a podmínku, že vytvoření proběhne před začátkem rezervace podle času serveru v UTC.

Rezervaci uloží do PostgreSQL ve stavu DRAFT a vrátí
HTTP 201 Created s vygenerovaným ID rezervace.

Automatizovaný integrační test odešle HTTP požadavek, ověří
status 201 a podle vráceného ID načte záznam z databáze.
Zkontroluje učebnu, uživatele, časový interval, počet účastníků
a stav DRAFT.

Tato end-to-end cesta bude spustitelná po C03 / před C04.
