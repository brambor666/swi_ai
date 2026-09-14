# Rezervační systém učeben
Swi profi projekt

## 2 Co rezervujeme

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
