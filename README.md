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
| States | DRAFT, PENDING_APPROVAL, CONFIRMED, CANCELLED, REJECTED, EXPIRED |
| Operations | Vytvořit rezervaci, potvrdit rezervaci, zrušit rezervaci, ověřit dostupnost učebny |
| Common rule | Dvě potvrzené rezervace stejné učebny se nesmějí časově překrývat |
| Boundary | Notification Service — odesílá uživateli oznámení o potvrzení nebo zrušení rezervace |

### Vlastní business rule

Počet účastníků rezervace nesmí překročit kapacitu učebny.

Podle [aktuální specifikace v0.2](docs/specification-v0.2.md) se kapacita kontroluje již při vytvoření. Časy jsou v UTC, frontend je převádí. Vytvoření a potvrzení jsou povoleny pouze před začátkem. Zrušení potvrzené rezervace je povoleno nejméně 2 hodiny před začátkem včetně přesné hranice; zrušení návrhu a dotaz na dostupnost nemají časové omezení. Rozhoduje čas serveru při provádění změny. Uživatel vytváří a ruší pouze vlastní rezervace; o potvrzení vlastního návrhu žádá systém, který rozhoduje podle dostupnosti a pravidel. U speciálních prostor žádost čeká na správce. Dostupnost může zjišťovat každý ověřený uživatel. Selhání notifikace nemění výsledek operace a oznámení se zahodí.

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


## Schvalování speciálních prostor — C02 v0.2

Běžné učebny se potvrzují automaticky. Přednáškový sál P1 vyžaduje schválení: uživatel vytvoří návrh a zvolí Potvrdit, čímž vznikne PENDING_APPROVAL. Čekající žádost neblokuje učebnu. Správce prostor ji může schválit nebo zamítnout. Schválení znovu kontroluje kapacitu, dostupnost a čas. Při začátku rezervace žádost vyprší do EXPIRED; vlastník ji může před začátkem zrušit.

Lokální správce má ID `admin`; seznam lze změnit proměnnou prostředí `RESERVATION_APPROVERS` (ID oddělená čárkou). Identita je simulovaná hlavičkou X-User-Id, produkční autentizace zůstává externím předpokladem.

Spuštění v PowerShellu ze složky `src`:

```powershell
docker compose -f .\src\docker-compose.yml up -d
.\mvnw.cmd spring-boot:run
```

Otevři http://localhost:8080. Pro demonstraci použij `user-1` a vytvoř budoucí žádost na P1. Pak přepni na `admin`, klikni Načíst čekající žádosti a rozhodni. Migrace V3 se provede automaticky; původní rezervace zachová. Testy: `mvn test` ze složky s pom.xml, se spuštěným Dockerem a lokální PostgreSQL (contextLoads ji používá).

Změnová analýza: [change-c02-approval.md](docs/change-c02-approval.md). Původní [v0.1](docs/specification-v0.1.md) je zachovaná. Diagramy zatím odpovídají v0.1;
