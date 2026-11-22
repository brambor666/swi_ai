# Project Frame

## Reservation domain
Učebny (vzdělávací, přednáškové nebo zasedací prostory).

## Purpose
Systém slouží studentům a vyučujícím k efektivní rezervaci sdílených školních prostor. Cílem je zabránit konfliktům v čase ("double-booking") a zajistit, že vybraná učebna kapacitně odpovídá plánované události.

## Users / Stakeholders
* Student
* Vyučující
* Správce budovy — stakeholder; ve v0.1 nemá samostatnou operaci ani oprávnění.

## Core concepts
* **Reservation** (Rezervace)
* **Resource** (Učebna)
* **User** (Uživatel - žadatel)

## Core operations
* Create reservation
* Confirm reservation — systémové potvrzení podle dostupnosti a pravidel; bez lidského schvalování ve v0.1
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

Kontrola probíhá již při vytvoření návrhu a znovu při potvrzení. Pravidla jsou popsána ve [specifikaci v0.1](specification-v0.1.md): čas v UTC, vytvoření a potvrzení pouze před začátkem, zrušení potvrzené rezervace nejméně 2 hodiny před začátkem včetně přesné hranice. Rozhoduje čas serveru při provádění změny. Zrušení návrhu a dotaz na dostupnost nemají časové omezení. Vytvoření a zrušení jsou pouze pro sebe. Dostupnost může zjišťovat každý ověřený uživatel. O potvrzení vlastního návrhu žádá uživatel; rozhoduje systém podle dostupnosti a pravidel.

## External / system boundary
Notification Service (zajišťuje asynchronní odesílání upozornění uživatelům při potvrzení nebo zrušení rezervace).

Při selhání služby se oznámení zahodí bez opakování; úspěšná změna rezervace zůstává platná.

## Assumption
Předpokládáme, že autentizaci uživatelů kompletně řeší externí systém (Identity Provider) a naše aplikace pracuje pouze s ověřenými uživatelskými ID.

## Unknown
Zatím není jisté, zda a jak se budou do systému synchronizovat pevné rozvrhy výuky z univerzitního informačního systému (zda půjdou naimportovat jako série klasických rezervací, nebo budou mít speciální datový model).

## Selected future pressure

Tlak na změnu z C01, přijatý v C02 jako pravidlo BR-04 pro zrušení potvrzené rezervace.
Category: C — Changeability

Concrete pressure:
V budoucnu může škola zavést pravidlo, že potvrzenou rezervaci
lze zrušit nejpozději 2 hodiny před jejím začátkem.

Why it is relevant to our reservation system:
Rušení rezervací na poslední chvíli ztěžuje využití učebny
ostatními uživateli. Systém by proto musel při rušení potvrzené
rezervace ověřit, že do jejího začátku zbývají alespoň 2 hodiny.


## Aktuální změna C02

Výše je zachovaný Project Frame a tlak na změnu z C01. Povinná změna C02 je schvalování speciálních prostor, nikoli dvouhodinové rušení. Analýza je v [change-c02-approval.md](change-c02-approval.md), aktuální chování v [specification-v0.2.md](specification-v0.2.md).

## C08 / CR-08 — Maximální délka nové rezervace

**Stav:** ACCEPT — limit 3 hodiny potvrzen členy projektového týmu dne 8. 11. 2026. Implementace a ověření nového pravidla: PLANNED pro C09. Vlastník změny: Jakub Juchelka.

**Zdroj a potřeba:** týmové rozhodnutí potvrzené členy projektového týmu. Zavést jednotné pravidlo pro délku jednotlivé nové rezervace: maximálně 3 hodiny pro všechny uživatele a učebny. Delší akce se řeší individuální domluvou se správcem mimo aplikaci.

**Before:** commit `838f81891799db31eee423125e56354c16c03e27`, [specifikace v0.2](specification-v0.2.md), OP-01 a BR-01/03–06. Vytvoření omezuje pořadí časů, kapacitu, vlastnictví a budoucí začátek; maximální délku neomezuje. [Výchozí konfigurace a testy](evidence-and-evolution.md#c08--testy-výchozího-stavu-pro-cr-08).

**Potvrzené pravidlo pro C09:** při Create musí být `0 < end − start <= 3 h`, počítáno z UTC časů, stejně pro všechny uživatele a běžné i schvalované učebny. Speciální prostory nadále podléhají schválení správce při dodržení stejného limitu 3 h. Změna se týká pouze nových návrhů. Všechny dříve uložené rezervace včetně DRAFT a PENDING_APPROVAL dál podléhají původním pravidlům; lze je potvrdit či schválit i při délce nad 3 h. Délku existující rezervace současné API nemění. Mimo rozsah: denní kvóty, nastavování limitu a změna storno lhůty.

**Navazující stav C09:** limit je implementovaný a lokálně ověřený v commitu `7b1a057`; výsledky testů a otevřené release podmínky jsou v [evidenci C09](evidence-and-evolution.md#c09--integration-ci-and-release-record). Výše uvedené PLANNED zachycuje stav při rozhodnutí C08.

### Podmínky přijetí pro C09

- **AC-01:** při ostatních platných vstupech délka 1 h i přesně 3 h vrátí 201 a uloží DRAFT s původními údaji.
- **AC-02:** délka nad 3 h (test 3 h + 1 s) vrátí 409 / BUSINESS_RULE_VIOLATION s vysvětlením limitu; nevznikne záznam ani oznámení.
- **AC-03:** nulový/záporný interval nadále vrátí 400; kapacita, vlastnictví a čas vytvoření se dál kontrolují podle v0.2. Pořadí chyb u více současně neplatných vstupů neurčujeme.
- **AC-04:** již uložený delší DRAFT lze potvrdit a PENDING_APPROVAL schválit, dovolují-li to původní pravidla. BR-02 proti překryvu a pravidla rušení/expirace zůstávají platná.

### Impact Matrix

Metoda: sledování OP-01 přes `ReservationController` → `ReservationService.createReservation` → `Reservation.createDraft` → repository → DB; kontrola formuláře, chyb, schvalování a migrací. Odkazy v matici vedou na příslušné artefakty.

| Oblast / zkontrolovaný artefakt | Rozhodnutí a důvod | Akce a důkaz dokončení v C09 |
|---|---|---|
| [Specifikace v0.2, OP-01, BR-01/04](specification-v0.2.md#op-01--create-reservation); [C05](c05_detailed_design.md) | CHANGE — nový limit při vytvoření | Nová revize specifikace zachová v0.2, popíše AC a výjimku starých dat; review |
| [Architektura, ADR-001/002/005–006](architecture-and-decisions.md#3-záznamy-o-architektonických-rozhodnutích-adr), [ADR-007](architecture-and-decisions.md#adr-007--vlastnictví-expirace-čekající-žádosti); [ReservationService.java](../src/src/main/java/cz/vsb/reservation/domain/service/ReservationService.java) | CHECK — vlastník vytvoření zůstává stejný | Review průchodu; architektonické testy |
| [Reservation.java](../src/src/main/java/cz/vsb/reservation/domain/model/Reservation.java), metoda createDraft | CHANGE — společná doménová kontrola nových návrhů | Kontrola délky pouze v createDraft, ne v konstruktoru načítajícím stará data; unit testy hranice |
| [ReservationController.java](../src/src/main/java/cz/vsb/reservation/infrastructure/web/ReservationController.java), [RestExceptionHandler.java](../src/src/main/java/cz/vsb/reservation/infrastructure/web/RestExceptionHandler.java) | CHECK — formát API i existující mapování 409 stačí | HTTP test AC-01/02, kontrola code/message a DB |
| [ReservationRepositoryAdapter.java](../src/src/main/java/cz/vsb/reservation/infrastructure/persistence/ReservationRepositoryAdapter.java), [migrace V1–V3](../src/src/main/resources/db/migration) | NO IMPACT na schéma — časy již existují, staré delší záznamy jsou povolené | Bez migrace a globálního DB limitu; integrační test načtení a potvrzení/schválení starých dat |
| [index.html](../src/src/main/resources/static/index.html), [app.js](../src/src/main/resources/static/app.js) | CHANGE — uživatel má znát omezení | Informace „Maximálně 3 hodiny“ u formuláře; ruční kontrola a srozumitelná serverová chyba |
| [Konfigurace](../src/src/main/resources/application.properties), [spuštění a deployment](../README.md), recovery | CHECK — pevný limit, žádný nový parametr ani proces | Stejné spuštění; release poznámka o účinnosti jen pro nové návrhy; při návratu k původní verzi se opět dovolí delší nové rezervace, data jsou kompatibilní |
| [Integrační testy](../src/src/test/java/cz/vsb/reservation/infrastructure/web), [evidence C08](evidence-and-evolution.md#c08--testy-výchozího-stavu-pro-cr-08) | CHANGE — nová hranice i kompatibilita | Unit/API testy AC-01–04 a regrese vytvoření, překryvu, rušení a schvalování |
| Jiné zápisy / provoz — [API controller](../src/src/main/java/cz/vsb/reservation/infrastructure/web/ReservationController.java) | CHECK — nebylo nalezeno uživatelské importní ani editační API | Limit platí pro aplikační Create; administrativní SQL ho nevynucuje. Při vzniku další vytvářecí cesty musí projít stejným pravidlem |

### Options Matrix

| Varianta | Splnění potřeby | Přínos | Náklady, rizika a obnova | Ověření |
|---|---|---|---|---|
| A: jednotný limit 3 h při Create | Splní AC | Malý zásah, stejné pravidlo pro všechny | Omezuje i legitimní celodenní akce; změna politiky vyžaduje release. Návrat ke starému kódu obnoví původní chování | Hranice 3 h, odmítnutí a kompatibilita starých dat |
| B: limit podle učebny, nyní všude 3 h | Splní AC při nastavení 3 h | Později dovolí různé potřeby učeben | Nový údaj v DB, migrace, validace konfigurace a více testů; návrat musí zachovat kompatibilitu schématu | Totéž pro různé konfigurace a migraci |

**Nečinnost:** delší nové rezervace zůstanou povolené; lze je řešit ruční dohodou se správcem, ale systém limit nevynutí.

**Rozhodnutí:** ACCEPT varianty A — pevný limit 3 h při Create, stejný pro všechny. Důvod: jednotná politika s malým rozsahem změny, bez nové konfigurace či schvalovacího mechanismu. Delší akce se řeší individuálně mimo aplikaci; uložené rezervace zůstávají podle původních pravidel.

**Schválení — POTVRZENO:** Filip Vodák, člen projektového týmu, 8. 11. 2026; rozhodnutí kriticky posoudil a potvrdil. Potvrzen jednotný limit 3 hodiny pro všechny uživatele a učebny. Delší akce se řeší individuálně mimo aplikaci; dříve uložené rezervace zůstávají zachované.

**Průběh review a změna návrhu:** původní návrh počítal s maximální délkou 4 hodiny. Při týmové domluvě byla přehodnocena hodnota limitu a návrh se změnil na 3 hodiny. Konečné rozhodnutí a podmínky přijetí AC-01–04 výše vycházejí z dohodnutého limitu 3 hodiny.

**Monitoring:** po zavedení sledovat požadavky na delší akce. Případné další požadavky vyhodnotit jako samostatnou změnu politiky.

**Předání do C09:** upravit createDraft, informaci formuláře a specifikaci; implementovat testy AC-01–04. Zopakovat existující integrační sady vytvoření, životního cyklu a schvalování, architektonické testy; doložit nový commit, příkazy, HTTP/DB výsledky a release z čistého checkoutu. Výstupem C08 je schválené rozhodnutí a ověřená výchozí baseline; implementace a ověření nového pravidla následují v C09.
