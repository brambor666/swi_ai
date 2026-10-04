# Rezervační systém učeben — přehled projektu

Přehled pro členy týmu: co systém dělá, jak je postavený, jak ho spustit a otestovat a co zbývá. Zadání a rozhodnutí jsou v `docs/` (specifikace v0.2 a zachovaná v0.1, `architecture-and-decisions.md`, `evidence-and-evolution.md`, `intent-and-change.md`). Tenhle dokument je doplněk, ne jejich náhrada.

## Co systém dělá

Studenti a vyučující rezervují školní učebny. Systém hlídá, aby se dvě potvrzené rezervace stejné učebny nepřekrývaly a aby počet účastníků nepřekročil kapacitu.

Pravidla aktuální specifikace C02 (v0.2):

| Pravidlo | Význam |
|---|---|
| Čas | Všechny časy jsou v UTC, do místního času je převádí frontend. |
| Překryv | Dvě potvrzené rezervace stejné učebny se nesmějí překrývat. Intervaly jsou `[začátek, konec)`, navazující rezervace (do 11:00 a od 11:00) kolize nejsou. Návrhy (`DRAFT`) a zrušené rezervace učebnu neblokují. |
| Kapacita | Počet účastníků nesmí překročit kapacitu učebny. Kontroluje se při vytvoření i při potvrzení. |
| Časové podmínky | Vytvoření a potvrzení jsou povoleny pouze před začátkem. Návrh lze zrušit kdykoli; potvrzenou rezervaci nejméně 2 hodiny před začátkem včetně přesné hranice (čas serveru, UTC). |
| Vlastnictví | Uživatel vytváří, potvrzuje a ruší jen své rezervace. Běžnou učebnu potvrzuje systém; speciální prostor schvaluje správce. |
| Dostupnost | Zjišťovat ji může každý ověřený uživatel, dotaz nic nemění. |
| Notifikace | Selhání Notification Service nemění výsledek operace, oznámení se zahodí bez opakování. |

Stavy: běžná učebna `DRAFT` → `CONFIRMED`; speciální `DRAFT` → `PENDING_APPROVAL` → `CONFIRMED` / `REJECTED` / `EXPIRED`. Vlastník může rušit podle časových pravidel do `CANCELLED`. Čekající žádost neblokuje; vyprší při začátku.

## Tech stack

Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 16 (v Dockeru), Flyway, JUnit 5, Testcontainers 2.0.5, ArchUnit. Vývojové prostředí IntelliJ IDEA.

## Co musíš mít nainstalované

1. **JDK 21**
2. **IntelliJ IDEA** (stačí Community)
3. **Docker Desktop**. Potřebuješ ho i pro `mvn test`, ne jen pro běh aplikace: integrační testy si přes Testcontainers samy spouštějí dočasnou PostgreSQL. Před spuštěním testů počkej, až Docker Desktop hlásí "Engine running".
4. Git a přístup k repozitáři

## Jak spustit aplikaci

```bash
git clone <URL repozitáře>
cd swi_ai/src/src
docker compose up -d        # PostgreSQL v Dockeru
```

V IntelliJ otevři složku `swi_ai/src` (tu s `pom.xml`, ne kořen repozitáře) jako Maven projekt a spusť `ReservationSystemApplication`. Flyway při startu vytvoří tabulky (`V1`) a vloží tři ukázkové učebny (`V2`): Učebna A1 (30), Učebna B2 (20) a Přednáškový sál P1 (120).

Frontend: po spuštění aplikace otevři `http://localhost:8080`. Stránku neotvírej dvojklikem na soubor, API by pak volala na špatné adrese.

Testy: `mvn test` nebo spuštění v IntelliJ.

Kdyby Testcontainers hlásily, že nenašly Docker, ověř nejdřív `docker info` v terminálu. Tým řešil kompatibilitu se starším Testcontainers a novým Docker Desktopem, vyřešila ji verze 2.0.5 (artefakty se od 2.0 jmenují `testcontainers-junit-jupiter` a `testcontainers-postgresql`).

## API

Identitu ověřeného uživatele zatím zastupuje hlavička `X-User-Id` (předpoklad: autentizaci řeší externí Identity Provider). Až bude JWT, změní se jediná třída `RequestIdentity`.

| Operace | Požadavek | Úspěch |
|---|---|---|
| Vytvořit návrh | `POST /reservations` s `{resourceId, userId, start, end, participantCount}` | `201` + `{id, state}` |
| Potvrdit | `POST /reservations/{id}/confirm` | `200` + `{id, state}` |
| Zrušit | `POST /reservations/{id}/cancel` | `200` + `{id, state}` |
| Dostupnost | `GET /resources/{resourceId}/availability?start=...&end=...` | `200` + `{available}` |
| Seznam učeben (rozšíření) | `GET /resources` | `200` + pole `{id, label, capacity}` |
| Moje rezervace (rozšíření) | `GET /reservations` | `200` + pole `{id, resourceId, userId, start, end, participantCount, state}` |

Zrušení je `POST`, ne `DELETE`, protože záznam zůstává a jen mění stav.

Poslední dva řádky tabulky jsou rozšíření mimo specifikaci v0.1: jen čtení, žádné pravidlo se jimi nemění, potřebuje je frontend. `GET /reservations` vrací pouze rezervace přihlášeného uživatele (BR-05), ve všech stavech, seřazené podle začátku.

Odmítnutí vždy vrací tělo `{code, message}`:

| Status | Kdy |
|---|---|
| 400 | neplatný vstup (prázdné ID, začátek ≥ konec, počet ≤ 0, chybějící nebo špatně formátovaná hodnota) |
| 401 | chybí hlavička `X-User-Id` |
| 403 | operace nad cizí rezervací nebo vytvoření pro někoho jiného |
| 404 | učebna nebo rezervace neexistuje |
| 409 | nadkapacita, nesplněná časová podmínka, překryv s potvrzenou rezervací, nepovolený přechod stavu |

Souběh potvrzení dvou kolidujících rezervací zachytí databázový `EXCLUDE` constraint (ADR-002) a vrátí `409`. Confirm a Cancel stejné rezervace drží řádkový zámek v transakci (ADR-005); po čekání se vyhodnotí aktuální stav a čas. Oznámení se předává až po commitu.

Příklad (PowerShell, `curl.exe`):

```powershell
curl.exe -i -X POST http://localhost:8080/reservations `
  -H "Content-Type: application/json" -H "X-User-Id: user-1" `
  -d '{\"resourceId\":1,\"userId\":\"user-1\",\"start\":\"2026-12-01T10:00:00\",\"end\":\"2026-12-01T11:00:00\",\"participantCount\":20}'
```

## Architektura (hexagon)

```
cz.vsb.reservation/
├── ReservationSystemApplication.java
├── domain/                        ŽÁDNÁ závislost na Springu, JPA ani logovací knihovně
│   ├── model/                     Resource, Reservation, ReservationState
│   ├── exception/                 doménové výjimky (mapují se na HTTP statusy)
│   ├── port/in/                   ReservationUseCase
│   ├── port/out/                  ReservationRepository, ResourceRepository, NotificationPort
│   └── service/                   ReservationService (orchestrace)
└── infrastructure/
    ├── config/                    BeanConfiguration (Clock UTC, registrace ReservationService)
    ├── persistence/               JPA entity + adaptéry repozitářů
    ├── notification/              LoggingNotificationAdapter (zatím jen loguje)
    └── web/                       controllery, DTO, RestExceptionHandler
```

Frontend je statická stránka v `src/main/resources/static/` (`index.html`, `app.js`, `style.css`) bez build nástrojů, Spring Boot ji servíruje na `/`. Uživatel zadává a vidí místní čas, do API se posílá UTC (BR-01). Přihlášení zatím simuluje pole s identitou, která se posílá v hlavičce `X-User-Id`.

Byznys pravidla žijí v doméně: kapacita, časová pravidla, vlastnictví a stavové přechody v `Reservation`, kontrola překryvu v `ReservationService` (potřebuje vidět ostatní rezervace). Aktuální čas se do domény předává jako parametr (`Clock` ve službě), takže pravidla se testují bez čekání a bez mockování systémových hodin.

## Testy

| Druh | Co ověřuje | Potřebuje Docker |
|---|---|---|
| Jednotkové (`domain`) | `Resource`, `Reservation`, `ReservationService` včetně hranic lhůty (08:00 projde, 08:00:01 ne) a příkladů dostupnosti ze specifikace | ne |
| Architektonické (`ArchitectureTest`) | doména nezná framework ani infrastrukturu, controllery volají jen inbound porty, adaptéry se neznají navzájem, JPA entity nezávisí na doméně | ne |
| Integrační — persistence | mapování na JPA a databázový `EXCLUDE` constraint proti překryvu | ano |
| Integrační — API | CP1 `POST /reservations`, celý životní cyklus (potvrzení, zrušení, dostupnost) a seznam učeben a vlastních rezervací přes HTTP proti reálné PostgreSQL | ano |

## Stav

Hotovo:

- Doména, porty, služba, persistence, REST API pro všechny čtyři operace
- Frontend: kontrola dostupnosti, vytvoření návrhu, potvrzení a zrušení rezervace, tabulka vlastních rezervací se stavy a důvody odmítnutí
- Chybové odpovědi 400/401/403/404/409 podle BR-06
- Ukázková data, testy všech výše uvedených druhů

Zbývá:

- Skutečná Notification Service místo logování
- Skutečná autentizace přes Identity Provider (místo hlavičky `X-User-Id`)
- Neznámé ze specifikace: synchronizace pevných rozvrhů z univerzitního informačního systému

## Rozhodnutí mimo ADR

- Jedno inbound rozhraní `ReservationUseCase` pro všechny operace.
- `GET /resources` a `GET /reservations` jsou rozšíření mimo specifikaci v0.1: jen čtení, potřebuje je frontend, BR-05 platí (uživatel vidí jen své rezervace).
- JPA entity jsou oddělené od doménových tříd, mapování se dělá ručně v adaptérech (stav je v entitě `String`).
- Chyby jsou pět doménových výjimek, každá odpovídá jednomu HTTP statusu.
- Přehled se zatím nezabývá rolemi uživatelů (student/vyučující), doména nese jen identifikátor.

## Git workflow

Feature branch `feature/<číslo-issue>-popis`, průběžné commity, pull request proti `main` s `Closes #<číslo>` v popisu, po schválení merge a smazání větve. Před založením další větve `git checkout main` a `git pull`.


## Změna C02 — v0.2

Přednáškový sál P1 má `requiresApproval=true`; ostatní ukázkové učebny false. Nové endpointy:

| Operace | API | Oprávnění |
|---|---|---|
| Čekající žádosti | GET /reservations/pending-approvals | správce |
| Schválit | POST /reservations/{id}/approve | správce |
| Zamítnout | POST /reservations/{id}/reject | správce |

Confirm speciální učebny vrací 200 / PENDING_APPROVAL, nikoli alokaci. Správce je serverový seznam ID `reservation.approvers`, výchozí admin, konfigurovatelný přes RESERVATION_APPROVERS. GET /resources nově vrací requiresApproval. Frontend ukazuje nové stavy a panel rozhodování; API ověřuje oprávnění nezávisle na viditelnosti panelu.

Expirace používá UTC serveru a začátek rezervace, je kontrolována každou sekundu a před seznamy / rozhodovacími operacemi. Business hranice je start, nikoli perioda timeru. Timer lze vypnout pomocí reservation.expiry.enabled=false; testy pak explicitně volají stejný expirační sken s řízenými hodinami. Zamítnutí a expirace nevyvolávají notifikaci potvrzení. Stav je uložen v PostgreSQL a po restartu se prošlé žádosti vyhodnotí.

Aktuální specifikace a revize: docs/specification-v0.2.md. Dopad: docs/change-c02-approval.md.
