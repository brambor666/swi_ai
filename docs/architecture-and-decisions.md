# Architektura a architektonická rozhodnutí

Tento dokument zachycuje hlavní technologická a architektonická rozhodnutí pro projekt "Rezervační systém učeben" a vysvětluje kontext, který k těmto rozhodnutím vedl.

Dokument popisuje cílovou architekturu. Požadované chování určuje [specifikace v0.1](specification-v0.1.md).

## 1. Technologický stack

Pro implementaci byl zvolen následující stack, který klade důraz na spolehlivost, transakční bezpečnost a snadnou údržbu.

*   **Jazyk:** Java 21
    *   *Důvod:* Průmyslový standard pro backendové enterprise aplikace. Verze 21 přináší vlastnosti jako `record` (ideální pro DTO a Value Objects) a Pattern Matching, které zpřehledňují doménovou logiku.
*   **Framework:** Spring Boot 4.1.1.
    *   *Důvod:* Zrychluje vývoj díky auto-konfiguraci, poskytuje robustní Dependency Injection a snadnou tvorbu REST API.
*   **Databáze:** PostgreSQL
    *   *Důvod:* PostgreSQL poskytuje ACID transakce a databázové omezení `EXCLUDE USING gist`, které používáme jako pojistku proti uložení překrývajících se potvrzených rezervací stejné učebny.
*   **Verzování schématu:** Flyway
    *   *Důvod:* Zajišťuje konzistentní a opakovatelné migrace databázových tabulek `resource` a `reservation` napříč prostředími. Identifikátor uživatele ukládáme ve sloupci `reservation.user_id`; samostatnou tabulku uživatelů nemáme.
*   **Build nástroj:** Maven
    *   *Důvod:* Deklarativní správa závislostí a standardizovaný build proces, snadno integrovatelný s CI/CD.
*   **Testování:** JUnit 5, Mockito, Testcontainers
    *   *Důvod:* Doménovou logiku lze testovat bleskově pomocí JUnit a Mockito. Pro databázové integrace použijeme Testcontainers, které nastartují reálný PostgreSQL v Dockeru, čímž eliminujeme falešnou pozitivitu in-memory databází (např. H2).

---

## 2. Architektonický styl: Hexagonální architektura

Systém je navržen pomocí **Hexagonální architektury** (známé také jako Ports and Adapters). Cílem je striktně oddělit čistou business logiku od technických detailů (databáze, HTTP rozhraní, externí služby).

### Rozvrstvení aplikace

1.  **Doménová vrstva (Core / Centrum hexagonu)**
    *   Neobsahuje **žádné** závislosti na frameworku (Spring) ani databázi.
    *   Obsahuje entity `Resource` a `Reservation`. Uživatel je reprezentován pouze identifikátorem `userId` v rezervaci, nikoli samostatnou entitou `User`.
    *   Řídí stavy: `DRAFT`, `CONFIRMED`, `CANCELLED`.
    *   Má vynucovat pravidlo: *Počet účastníků rezervace nesmí překročit kapacitu učebny.* Existující implementace kontroluje horní mez při Confirm; 
    
2.  **Porty (Rozhraní na okraji domény)**
    *   **Inbound porty (Driving):** Rozhraní definující operace, které lze se systémem provádět (Vytvořit, potvrdit, zrušit rezervaci, ověřit dostupnost).
    *   **Outbound porty (Driven):** Rozhraní, která doména potřebuje k fungování (např. `ReservationRepository` pro uložení dat, `NotificationPort` pro odeslání zprávy).

3.  **Adaptéry (Infrastruktura)**
    *   **Inbound adaptéry:** REST Controllery, které přijímají HTTP požadavky, mapují je na příkazy a volají Inbound porty.
    *   **Outbound adaptéry:**
        *   *PostgresAdapter:* Implementuje repozitáře pomocí Spring Data JPA a překládá doménové objekty na databázové entity.
        *   *NotificationAdapter:* Implementuje `NotificationPort` (naše Boundary). Pro lokální vývoj bude pouze logovat do konzole, pro produkci může volat externí e-mailové API.

---

## 3. Záznamy o architektonických rozhodnutích (ADR)

### ADR-001: Použití Hexagonální architektury místo vrstvené (N-Tier)
*   **Kontext:** Potřebujeme aplikovat jasná doménová pravidla a integrovat externí Boundary (`Notification Service`). Běžná vrstvená architektura často vede k prolnutí databázových entit s business logikou.
*   **Rozhodnutí:** Použijeme Hexagonální architekturu.
*   **Důsledky:** 
    *   *Pozitivní:* Doménová pravidla (kapacita, stavy) jsou 100% nezávislá na technologiích a snadno testovatelná. Notifikace lze snadno mockovat.
    *   *Negativní:* Vyšší režie na psaní kódu (nutnost mapovat mezi DTO, Doménovými modely a JPA entitami).

### ADR-002: Zajištění pravidla "Rezervace se nesmějí překrývat"
*   **Kontext:** Dvě potvrzené rezervace stejné učebny se nesmějí časově překrývat. Může nastat situace, kdy dva uživatelé potvrdí DRAFT rezervaci na stejný čas ve stejnou milisekundu.
*   **Rozhodnutí:** `ReservationService` před potvrzením rezervace ověřuje překryv s existujícími potvrzenými rezervacemi přes `ReservationRepository`. Databázovou pojistku tvoří již implementovaný constraint `no_overlapping_confirmed_reservations` v migraci `V1__init.sql`. Používá `EXCLUDE USING gist` s rozšířením `btree_gist` nad `resource_id` a intervalem `tsrange(start_time, end_time)`. Platí pouze pro stav `CONFIRMED`; rezervace ve stavech `DRAFT` a `CANCELLED` tímto omezením nejsou blokovány. Navazující intervaly bez překryvu jsou povolené.
*   **Důsledky:** PostgreSQL odmítne konfliktní zápis i při obejití aplikační kontroly. Integrační test s reálnou PostgreSQL přes Testcontainers ověřuje odmítnutí druhé překrývající se potvrzené rezervace při postupném vkládání. Skutečný souběh dvou transakcí zatím tímto testem ověřen není.

### ADR-003: Modelování uživatele
*   **Kontext:** Uživatel může být Student nebo Vyučující. Z hlediska rezervace se ale jejich role neliší.
*   **Rozhodnutí:** Pro účely tohoto ohraničeného kontextu (Bounded Context) modelujeme uživatele pouze jako `userId` v doménovém objektu `Reservation`, uložené jako `user_id` v tabulce `reservation`. Samostatná entita ani tabulka `User` neexistuje. Autentizaci a rozlišování rolí má podle návrhu zajišťovat externí systém identity (např. přes JWT tokeny v REST adaptéru); tato integrace zatím není implementována.

### ADR-004: Read-only endpointy pro frontend

*   **Kontext:** Specifikace v0.1 definuje čtyři operace (vytvořit, potvrdit, zrušit, ověřit dostupnost) a žádná z nich nevrací seznam. Frontend potřebuje vědět, jaké učebny existují a jaké rezervace má přihlášený uživatel.
*   **Rozhodnutí:** Přidat `GET /resources` (seznam učeben) a `GET /reservations` (vlastní rezervace přihlášeného uživatele). Obě operace jsou jen čtení a nemění žádné byznys pravidlo ani stav.
*   **Důsledky:**
    *   *Pozitivní:* Frontend nemusí mít učebny natvrdo ani si pamatovat rezervace v prohlížeči. Pravidlo BR-05 platí i pro čtení: uživatel vidí pouze své rezervace.
    *   *Negativní:* API je širší než specifikace v0.1. Pokud se specifikace bude revidovat, měla by tyto operace převzít.


### ADR-005: Souběžné změny stejné rezervace

Confirm a Cancel běží v transakci a načítají rezervaci s PostgreSQL řádkovým zámkem (`PESSIMISTIC_WRITE`). Čekající operace po získání zámku vyhodnotí aktuální stav a čas serveru. Zámek trvá do commitu nebo rollbacku. Tím se zabrání pozdnímu přepsání CANCELLED a dvěma úspěšným opakováním stejného přechodu. Různé rezervace zůstávají chráněné proti kolidující alokaci constraintem z ADR-002. Oznámení se předává až po commitu; selhání se zahodí podle BR-07. Nevýhodou je čekání na právě měněný řádek. Integrační HTTP testy pokrývají Confirm/Cancel, opakované Confirm i Cancel a dvě konfliktní potvrzení.


### ADR-006: Perzistentní schvalovací proces a expirace

Učebna nese requiresApproval, čekání a výsledky rozhodnutí jsou stavy uložené rezervace. Běžná učebna potvrzuje automaticky, speciální přejde do PENDING_APPROVAL bez blokace. Schvalovatelé jsou serverový seznam ověřených ID; klient neposílá důvěryhodnou roli. Lokalní identita zůstává prototypová X-User-Id. Approve a Reject používají stejnou transakční ochranu jako Confirm/Cancel (ADR-005), proti kolidujícím schválením platí ADR-002.

Platnost čekání končí při start. Infrastructure timer po jedné sekundě provádí idempotentní podmíněný SQL přechod PENDING_APPROVAL → EXPIRED. Stejný přechod běží před seznamy a rozhodnutím v oddělené transakci, aby odmítnuté pozdní rozhodnutí nevrátilo zpět expiraci. Doména navíc kontroluje čas po získání zámku. Interval timeru je technické rozhodnutí, ne business lhůta; může mít zpoždění při nedostupnosti databáze. Perzistence umožní po restartu dohledat prošlé žádosti. Zamítnutí, čekání a expirace neposílají oznámení potvrzení; Approve oznámí až po commitu.

Driver pro C03: oddělení časového procesu, správa rolí a bezpečný souběh timeru s rozhodnutím. Distribuované instance mohou provést sken opakovaně; podmíněný zápis je idempotentní. Produkční plánování a autentizace nejsou cílem tohoto prototypu.
