# Architektura a architektonická rozhodnutí

Tento dokument zachycuje hlavní technologická a architektonická rozhodnutí pro projekt "Rezervační systém učeben" a vysvětluje kontext, který k těmto rozhodnutím vedl.

## 1. Technologický stack

Pro implementaci byl zvolen následující stack, který klade důraz na spolehlivost, transakční bezpečnost a snadnou údržbu.

*   **Jazyk:** Java 21
    *   *Důvod:* Průmyslový standard pro backendové enterprise aplikace. Verze 21 přináší vlastnosti jako `record` (ideální pro DTO a Value Objects) a Pattern Matching, které zpřehledňují doménovou logiku.
*   **Framework:** Spring Boot 3.x
    *   *Důvod:* Zrychluje vývoj díky auto-konfiguraci, poskytuje robustní Dependency Injection a snadnou tvorbu REST API.
*   **Databáze:** PostgreSQL
    *   *Důvod:* Relační databáze je nezbytná pro vynucení ACID transakcí. Aplikace musí řešit souběžné vytváření rezervací, k čemuž využijeme pokročilé zamykání a izolaci transakcí v PostgreSQL.
*   **Verzování schématu:** Flyway
    *   *Důvod:* Zajišťuje konzistentní a opakovatelné migrace databázových tabulek (`Resource`, `Reservation`, `User`) napříč prostředími.
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
    *   Obsahuje entity: `Resource`, `Reservation`, `User`.
    *   Řídí stavy: `DRAFT`, `CONFIRMED`, `CANCELLED`.
    *   Vynucuje pravidla: *Počet účastníků rezervace nesmí překročit kapacitu učebny.*
    
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
*   **Rozhodnutí:** Logika ověření překryvu bude primárně v doménové vrstvě (načteme existující rezervace a ověříme časové okno). Pro zabránění "race condition" (souběhu) při uložení do databáze využijeme transakční izolaci v PostgreSQL a případně databázový constraint (např. unikátní index nad překrývajícími se intervaly přes rozšíření `btree_gist`, nebo zamykání řádku učebny).
*   **Důsledky:** Nutnost testovat tyto souběhy pomocí integračních testů s reálnou databází (Testcontainers).

### ADR-003: Modelování uživatele
*   **Kontext:** Uživatel může být Student nebo Vyučující. Z hlediska rezervace se ale jejich role neliší.
*   **Rozhodnutí:** Pro účely tohoto ohraničeného kontextu (Bounded Context) budeme uživatele modelovat pouze jako ID uživatele. Rozlišování rolí bude delegováno na externí systém identity (např. přes JWT tokeny v REST adaptéru), doménový model ponese pouze identifikátor vlastníka rezervace.