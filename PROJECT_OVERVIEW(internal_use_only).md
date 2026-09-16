# Rezervační systém učeben — přehled projektu

Tento dokument shrnuje, co je projekt zač, jak vznikl, jak je postavený a jak na něm pokračovat. Určeno pro členy týmu, kteří se k projektu připojují nebo se v něm potřebují zorientovat.

## Co systém dělá

Rezervační systém pro školní učebny. Studenti a vyučující si rezervují učebny na určitý čas, systém hlídá, že se dvě potvrzené rezervace stejné učebny časově nepřekrývají a že počet účastníků nepřekročí kapacitu učebny. Kompletní zadání je v `docs/` (README.md, architecture-and-decisions.md, evidence-and-evolution.md, intent-and-change.md) — tenhle dokument je doplněk, ne náhrada.

## Tech stack

- **Java 21**, **Spring Boot 4.1.1**, **Maven**
- **PostgreSQL 16** (běží v Dockeru, ne nainstalovaná natvrdo na stroji)
- **Flyway** — verzování databázového schématu
- **JUnit 5**, **Testcontainers**, (**ArchUnit** naplánovaný, zatím nevyužitý)
- **IntelliJ IDEA**

## Co potřebuješ mít nainstalované, abys mohl/a pracovat na projektu

1. **JDK 21** — https://adoptium.net nebo přímo z Oracle
2. **IntelliJ IDEA** (Community edice stačí)
3. **Docker Desktop** — https://www.docker.com/products/docker-desktop/. Nutné i pro `mvn test` (viz níže), ne jen pro lokální spuštění aplikace. Po instalaci nezapomeň Docker Desktop spustit a počkat, až ikonka velryby ukazuje "running".
4. Git / GitHub přístup k repozitáři

## Jak spustit projekt lokálně

```bash
git clone <URL repozitáře>
cd swi_ai/src/src
docker compose up -d          # nastartuje PostgreSQL v Dockeru
```

Pak otevři složku `swi_ai/src/src` (ne kořen repa!) v IntelliJ jako Maven projekt a spusť `ReservationSystemApplication` (třída v `cz.vsb.reservation`). Flyway při startu automaticky vytvoří tabulky.

Ověření, že databáze běží správně:
```bash
docker exec -it src-postgres-1 psql -U rezervace -d rezervace
\dt
```
Měl bys vidět `resource`, `reservation`, `flyway_schema_history`.

**Spuštění testů:** `mvn test` (nebo přes IntelliJ). Testy s `@Testcontainers` (integrační) si samy nastartují dočasnou PostgreSQL v Dockeru — proto Docker Desktop musí běžet i jen kvůli testům, ne jen kvůli běhu aplikace.

## Architektura — hexagon (Ports & Adapters)

Podle `architecture-and-decisions.md` (ADR-001) je aplikace rozdělená do tří vrstev:

```
cz.vsb.reservation/
├── ReservationSystemApplication.java
├── domain/                          ← ŽÁDNÁ závislost na Springu/JPA
│   ├── model/                       Resource, Reservation, ReservationState
│   ├── port/
│   │   ├── in/                      ReservationUseCase (co systém umí)
│   │   └── out/                     ReservationRepository, ResourceRepository,
│   │                                NotificationPort (co systém potřebuje)
│   └── service/                     ReservationService — orchestrace, žádná byznys logika
└── infrastructure/
    ├── persistence/                 JPA entity + adaptéry implementující out porty
    ├── web/                         REST controllery (zatím neimplementováno)
    └── notification/                NotificationPort adaptér (zatím neimplementováno)
```

**Klíčové pravidlo:** třídy v `domain` nesmí importovat nic z `infrastructure`, Springu ani JPA. Doména se testuje čistými JUnit testy bez databáze, bez Spring kontextu — proto běží v milisekundách.

### Doménový model

- **`Resource`** — učebna (id, label, capacity), immutable, validuje se v konstruktoru
- **`ReservationState`** — enum `DRAFT`/`CONFIRMED`/`CANCELLED` s metodou `canTransitionTo()`, která hlídá povolené přechody
- **`Reservation`** — rezervace, `confirm(Resource)` ověří jak stavový přechod, tak kapacitu; `cancel()` ověří přechod
- **`ReservationService`** — implementuje `ReservationUseCase`, orchestruje volání portů a doménových metod, řeší kontrolu překryvu rezervací (ADR-002) ještě před spolehnutím na databázový constraint

### Databáze

Schéma je v `db/migration/V1__init.sql`. Klíčový detail: **`EXCLUDE USING gist` constraint** na tabulce `reservation` — databázová pojistka proti souběhu (dva uživatelé potvrdí stejný čas ve stejnou milisekundu), nezávislá na doménové validaci v aplikaci. Testuje se v `ReservationRepositoryAdapterIntegrationTest` přímým JDBC insertem, který obchází doménu.

**Flyway pravidlo:** jednou spuštěná migrace (`V1__init.sql`) se už neupravuje. Další změna schématu = nový soubor `V2__popis.sql`.

## Co je hotové (k dnešnímu dni)

- ✅ Projekt vygenerovaný přes Spring Initializr, Maven build funkční
- ✅ PostgreSQL v Dockeru + Flyway migrace (`resource`, `reservation` tabulky, EXCLUDE constraint)
- ✅ Doménový model: `Resource`, `Reservation`, `ReservationState` — plně otestováno JUnit testy
- ✅ Porty: `ReservationUseCase` (in), `ReservationRepository`, `ResourceRepository`, `NotificationPort` (out)
- ✅ `ReservationService` — implementace use case, včetně kontroly překryvu (ADR-002)
- ✅ Persistence adaptér: JPA entity + ruční mapování na doménové objekty, `ReservationRepositoryAdapter`, `ResourceRepositoryAdapter`
- ✅ Integrační test s Testcontainers ověřující mapování i databázový constraint

## Co zbývá udělat

- ⬜ `NotificationPort` adaptér — zatím jen naplánovaný, pro lokální vývoj má stačit logování do konzole (viz ADR v `architecture-and-decisions.md`)
- ⬜ REST controllery (`infrastructure/web`) — inbound adaptér volající `ReservationUseCase`
- ⬜ ArchUnit testy chránící hranice hexagonu (`domain` nesmí importovat `infrastructure`/Spring) — popsáno v `evidence-and-evolution.md`, zatím neimplementováno
- ⬜ Endpoint/logika pro `checkAvailability` (use case metoda existuje, chybí jí REST vstupní bod)
- ⬜ Zvážit `CreateReservationCommand` DTO, pokud `createReservation` naroste přes stávající počet parametrů

## Poznámky k rozhodnutím, které nejsou v ADR

Tyhle detaily jsme řešili v průběhu implementace a nejsou (zatím) zapsané v `architecture-and-decisions.md` — může se hodit je tam časem doplnit:

- **Definice překryvu intervalů:** `start1 < end2 AND start2 < end1` — rezervace, která končí přesně v čase, kdy další začíná, se nepovažuje za překryv (otestováno v `ReservationServiceTest.confirmReservation_succeeds_whenReservationsAreBackToBack`)
- **Inbound port je jedno rozhraní** `ReservationUseCase` se všemi operacemi, ne rozhraní pro každou operaci zvlášť — jednodušší pro rozsah projektu
- **JPA entity jsou oddělené od doménových tříd** (ne anotace přímo na doméně) — víc kódu (ruční mapování v adaptérech), ale doména zůstává 100% nezávislá na frameworku podle ADR-001
- **Spring Boot 4 modularizace:** pozor, `flyway-core` sám o sobě auto-konfiguraci nezapne — je potřeba i `spring-boot-starter-flyway` (Spring Boot 4 rozdělil `spring-boot-autoconfigure` na moduly per-technologie)

## Git workflow

Používáme feature branch + PR flow: branch pojmenovaná `feature/<číslo-issue>-popis`, práce s průběžnými commity, PR proti `main` s `Closes #<číslo>` v popisu, po review merge a smazání větve. Podrobný postup krok za krokem je v prvních zprávách tohoto chatu, pokud by ho někdo potřeboval znovu projít.
