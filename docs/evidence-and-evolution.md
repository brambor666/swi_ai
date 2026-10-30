# C01 — Technické ověření

**Otázka / neznámá:**
Lze rezervaci spolehlivě uložit do skutečné PostgreSQL databáze,
znovu ji načíst a zároveň zabránit překrývajícím se potvrzeným
rezervacím?

**Co jsme udělali:**
Implementovali jsme persistence vrstvu pomocí JPA a PostgreSQL,
vytvořili databázové schéma pomocí Flyway a přidali integrační
testy využívající Testcontainers. Testy ověřují uložení a načtení
rezervace a také databázový constraint zabraňující překryvu
potvrzených rezervací. Ověření jsme spustili příkazem mvn test.

**Pozorovaný výsledek:**
PostgreSQL kontejner se úspěšně spustil a Flyway vytvořil
databázové schéma. Všechny čtyři persistence integrační testy
prošly. Po uložení a načtení rezervace byla ověřena shoda ID,
uživatele a stavu DRAFT. Databázový constraint odmítl druhou
překrývající se potvrzenou rezervaci stejné učebny.
Celkem prošlo všech 18 testů bez chyb a bez přeskočení.
Build skončil výsledkem BUILD SUCCESS.

**Rozhodnutí / dopad výsledku:**
Budeme nadále používat PostgreSQL pro persistence vrstvu
a databázový constraint jako dodatečnou ochranu proti souběžným
konfliktním rezervacím. Persistence zůstane oddělena od domény
přes repository porty a integrační testy persistence budeme
dál ověřovat pomocí Testcontainers.

## Evidence C02: sjednocení v0.1 a souběh

**Baseline:** specification-v0.1.md, včetně revize REQ-01 až REQ-12. Tato úprava sjednotila časová pravidla a souběh; schvalování je popsáno samostatně ve v0.2.

**Nalezený nesoulad a řešení:** implementace a testy vyžadovaly dvě hodiny před začátkem i pro Create, Confirm a Cancel návrhu. Opraveny podle BR-04: Create/Confirm pouze před začátkem, Cancel návrhu bez časového omezení, Cancel potvrzené rezervace včetně hranice start − 2 h. Opraven byl i přehled projektu a text ve frontendu.

**Výsledky ověření:**

- Kompletní Maven `test`: 90 testů, 0 selhání, 0 chyb, 0 přeskočení, BUILD SUCCESS.
- Po přidání testu vynuceného čekání na zámek spuštěno `-Dtest=ReservationLifecycleApiTest test`: všech 22 testů prošlo bez selhání, chyb a přeskočení. Ostatní testy po této poslední změně nebyly opakovány.
- Create: uložený DRAFT; neplatný vstup, překročení kapacity a začátek v minulosti odmítnuty. Doménový test ověřil úspěch sekundu před začátkem a odmítnutí přesně na začátku.
- Availability: volný i kolidující interval, navazující hranice, odmítnutí neplatného dotazu a absence změn.
- Confirm: úspěšné potvrzení i méně než dvě hodiny před začátkem; odmítnutí konfliktu a potvrzení přesně na začátku.
- Cancel: zachovaný CANCELLED a uvolněná dostupnost; opakování odmítnuto; návrh zrušen i po konci; potvrzená rezervace na dvouhodinové hranici zrušena, po hranici odmítnuta.
- Souběžné HTTP požadavky proti PostgreSQL přes Testcontainers: Confirm/Cancel stejné rezervace před i po dvouhodinové hranici, dvě Confirm stejné rezervace, dvě Cancel stejné rezervace a dvě konfliktní Confirm různých rezervací. Pozdní potvrzení neobnovilo CANCELLED; opakované přechody měly nejvýše jeden úspěch; konfliktní alokace právě jednu potvrzenou rezervaci.
- Vynucené čekání: necommitnutý zápis CANCELLED držel řádek, test ověřil skutečné čekání Confirm na databázový zámek. Po commitu zrušení Confirm vrátil 409 / INVALID_STATE, konečný stav zůstal CANCELLED.

**Architektonický driver:** atomická změna životního cyklu a vyhodnocení aktuálního stavu/času po čekání. Řešení dokumentuje ADR-005; oznámení se předává až po commitu.

**Zbývající předpoklady:** externě ověřená identita je při lokálním běhu simulována hlavičkou; synchronizace rozvrhů zůstává neznámá. Schvalovací proces je popsán v následující části.

**Commit aplikace:** `eb64d8862a8019d06964a0f4eddf82b021d553cb` obsahuje sjednocení v0.1 i následný schvalovací proces v0.2. Výsledky výše zachycují ověření před přidáním schvalování.


## Evidence C02: schvalovací změna v0.2

**Baseline:** specification-v0.2.md. Původní v0.1 zůstává zachovaná. Analýza dopadu je v change-c02-approval.md. Diagramy v0.2 jsou v docs/diagrams: use-case-v0.2.png, state-v0.2.png, activity-confirm-reservation-v0.2.png, activity-approve-reservation-v0.2.png a activity-cancel-reservation-v0.2.png.

**Shrnutí dopadu:** Create a časová dostupnost beze změny. Confirm speciálního prostoru vytvoří PENDING_APPROVAL. Nový správce schvaluje / zamítá; Approve znovu ověřuje čas, kapacitu a konflikt. Pending lze zrušit před začátkem a při začátku vyprší. Pouze CONFIRMED blokuje. Ukázkový P1 vyžaduje schválení, běžné A1/B2 nikoli. Migrace V3 přidává příznak a stavy bez změny uložených rezervací.

**Výsledky ověření:**

- Kompletní Maven `test` schvalovacího procesu: 105 testů, 0 selhání, 0 chyb, 0 přeskočení, BUILD SUCCESS. Obsahuje dřívější úspěšné i negativní příklady čtyř základních operací.
- Po doplnění ochrany při změně příznaku učebny: `-Dtest=ApprovalWorkflowApiTest,ReservationTest,ReservationServiceTest,ArchitectureTest test`, 63 testů bez selhání, chyb a přeskočení, BUILD SUCCESS. V této sadě je 15 schvalovacích integračních testů. Zbytek celé sady po této změně nebyl opakován.
- Speciální Create → DRAFT; Confirm → PENDING_APPROVAL; čekání neblokovalo dostupnost. Běžná učebna dále přešla přímo do CONFIRMED.
- Opožděné Approve sekundu před startem → CONFIRMED a nedostupný interval. Opakované schválení odmítnuto.
- Neoprávněné Approve/Reject i seznam správce → 403, bez identity → 401; stav nezměněn.
- Reject → REJECTED, dostupnost zůstala volná; opakované Reject, následné Approve a Cancel odmítnuty.
- Přesně na startu → EXPIRED; Approve/Reject/Cancel odmítnuty; interval neblokován. Stejný expirační sken jako timer byl navíc explicitně spuštěn bez uživatelské operace a uložil EXPIRED.
- Cancel čekající žádosti sekundu před startem → CANCELLED; cizí Cancel odmítnuto, následné Approve odmítnuto.
- Dvě čekající žádosti stejného intervalu: první Approve uspělo, druhé odmítnuto a zůstalo PENDING_APPROVAL; následné Reject uspělo.
- Snížení kapacity během čekání způsobilo odmítnutí Approve bez změny žádosti. Rozhodnutí nad DRAFT a neznámou rezervací odmítnuto.
- Čtení z databáze zachovalo pending; po posunu řízených hodin seznam vlastníka vrátil uložené EXPIRED. Restart aplikace nebyl samostatně testován. Stav a termín žádosti jsou uložené v databázi.
- Souběh přes HTTP / PostgreSQL: Approve/Cancel stejné rezervace, dvojí Approve, Approve/Reject a Approve dvou kolidujících žádostí. Výsledky odpovídaly postupnému pořadí, žádné obnovení CANCELLED ani dvojí alokace.
- Změna requiresApproval na false u již čekající žádosti neumožnila vlastníkovi obejít správce: Confirm → 409, správce Approve → 200.
- Frontend: `node --check app.js` a kontrola vazeb na elementy prošly; vizuální ověření v prohlížeči nebylo provedeno.

**Nalezené nesoulady a řešení:** přidán explicitní zákaz Confirm z jiného stavu než DRAFT, aby nově povolený přechod pending → confirmed nemohl obejít správce. Timer v testech vypnut a stejný sken volán explicitně s řízenými hodinami, aby cached Spring kontext nevolal již ukončené testovací kontejnery. V běžné aplikaci timer zůstává zapnut.

**Drivery pro C03:** perzistentní asynchronní proces, obnova časového stavu, autorizace správce, atomický souběh rozhodnutí/rušení/expirace, oznámení po commitu. Rozhodnutí viz ADR-005 a ADR-006.

**Zbývající předpoklady / neznámé:** lokální X-User-Id simuluje skutečnou identitu; oznámení pouze loguje; synchronizace rozvrhů není rozhodnutá.

**Commit aplikace:** `eb64d8862a8019d06964a0f4eddf82b021d553cb` — Complete C02 reservation approval workflow and verification. Tento commit obsahuje ověřenou implementaci v0.2. Test reporty jsou lokálně v src/target/surefire-reports; souhrn v src/target/v02-verification.log a v02-final-check.log.

## C03 — Evidence architektury

**Baseline:** Specification Baseline v0.2, scénář OP-05 — Approve Reservation a související expirace OP-08.

**Část A:** V [architecture-and-decisions.md](architecture-and-decisions.md) jsme namapovali schválení a konfliktní větev na kód, dohledali stav, pravidlo BR-02 a závislosti a vytvořili AS-IS diagram. Nález: schválení a expirace měnily stejný stav různými cestami; expiraci spouštěl timer i kontrola před operacemi přímo přes repository.

**Drivery:** Souběh schválení a expirace, perzistentní čekání a obnova po restartu, nekolidující alokace, oprávnění správce a oznámení po commitu. Zdrojové požadavky a pravidla jsou uvedené v B.

**Rozhodovací otázka:** Kde má být vlastněna expirace čekající žádosti a jak má spolupracovat se schválením, aby při souběhu platily BR-06 a BR-09?

**Alternativy:** A — společné řízení v jedné aplikaci; B — samostatný expirační worker nad stejnou databází. Porovnání pěti kritérií je v E2.

**Průchod scénářem:** V E3 jsme porovnali schválení se souběžnou expirací, čekání na zámek do startu a výpadek expiračního procesu. Obě varianty vyžadují kontrolu aktuálního času a koordinaci uložených změn.

**ADR:** ADR-007 přijímá alternativu A. Reservation Management vlastní schválení i expiraci; timer pouze vyžádá kontrolu. Přijímáme odložení uložené expirace při výpadku aplikace. Rozhodnutí přehodnotíme při požadavku na expiraci během odstávky aplikace nebo při významném zatížení skenem.

**Pohledy:** V architecture-and-decisions.md jsou doménový třídní model (C1), kontext (G1), statická architektura (G2), vlastnictví přechodů (G3), runtime/deployment (G4), návrhová sekvence (H1) a zaměřený návrhový třídní diagram (H2). G3 navazuje na statechart v0.2. Všech pět logických prvků běží v jedné aplikaci s PostgreSQL.

**Nalezené a opravené rozpory mezi pohledy:** Před změnou kódu jsme v H1 doplnili společnou operaci expirace z H2 a upřesnili umístění kontroly oprávnění uvnitř transakce. Kontrola sedmi vazeb je v I.

**AS-IS → TO-BE delta:** V J jsme určili CHANGE pro společnou operaci expirace, volání z timeru a volání před operacemi. KEEP zachovává pravidla schválení, databázovou ochranu, uložené čekání, identitu a oznámení. VERIFY pokrývá chování a závislosti timeru.

**Změny implementace:** Doplnili jsme `ReservationUseCase.expirePendingApprovals()` a její realizaci v `ReservationService`. `ApprovalExpiry` volá inbound port. `BeanConfiguration` používá společnou operaci v samostatné transakci před následným rozhodnutím. Schéma, pravidla a HTTP endpointy jsme neměnili.

**Ověření chování:** Dne 11. 10. 2026 jsme ze složky `src` spustili:

```powershell
.\mvnw.cmd '-Dtest=ReservationTest,ReservationServiceTest,ArchitectureTest,ApprovalWorkflowApiTest,ReservationLifecycleApiTest' test
```

Výsledek: **86 testů, 0 selhání, 0 chyb, 0 přeskočení, BUILD SUCCESS**. Integrační testy spustily aplikaci přes HTTP proti PostgreSQL v Testcontainers. Prošly úspěšné schválení, konfliktní větev, hranice expirace, sken bez uživatelské operace i souběžné změny. Konkrétní doklady jsou v L1.

**Architektonické pravidlo a výsledek:** Expiry Trigger vyžádá expiraci přes inbound port a nezná repository, persistence, doménové objekty/službu, hodiny ani transakční API. `ArchitectureTest.expiryTriggerUsesInboundPort` prošel spolu se čtyřmi původními pravidly. Samostatné zopakování: `.\mvnw.cmd '-Dtest=ArchitectureTest' test` ze složky `src`.

**Zbývající nejistoty / rizika:** Identitu lokálně simuluje X-User-Id, oznámení realizuje logovací adaptér. Restart aplikace nebyl samostatně testován; ověřili jsme načtení uloženého čekání a expiraci při posunu hodin. Při výpadku aplikace se zápis EXPIRED odloží. Synchronizace pevných rozvrhů zůstává nevyřešený předpoklad projektu.

**Commit implementace:** `c0642a7f74a10819649aa694ccd28cb18533071f` — Route approval expiry through reservation use case.

## C06 — Internal Evidence Assessment

**Assessed baseline:** `c0642a7f74a10819649aa694ccd28cb18533071f`.

**Nezávislý assessment:** Aneta Wágnerová, Eliška Bučková, 23. 10. 2026; předaný soubor `SWI_C06_Assessment_Form_CZ.md`.

### F1 — Chybí úplný řetězec C04 Quality & Measurement

**Claim / expected:** Projekt má doložit konkrétní řetězec Quality Concern → Criterion → Measurement → Decision.

**Observed:** Na hodnocené baseline existuje tlak Changeability v [intent-and-change.md](intent-and-change.md), výsledky funkčních a architektonických testů v tomto dokumentu a ADR v [architecture-and-decisions.md](architecture-and-decisions.md). Chybí jejich explicitní propojení přes vyhodnotitelné kritérium C04, odpovídající měření a rozhodnutí.

**Ověření:** Dne 30. 10. 2026 provedeno statické porovnání dokumentace na assessed baseline. `git ls-tree -r --name-only c0642a7f74a10819649aa694ccd28cb18533071f docs` identifikoval dostupné dokumenty; `git show <assessed-baseline>:docs/<soubor>` umožňuje zopakovat porovnání původního obsahu. Nález se týká chybějící evidence, nikoli prokázané chyby běhového chování.

**Výsledek ověření:** VERIFIED FINDING.

**Disposition:** ACCEPTED.

**Stav:** OPEN.

Assessment neobsahuje další ověřený finding; F2 a F3 jsou REJECTED CANDIDATE / N/A.

## C07/C08 — A. Technická obhajoba

**Vybraná operace:** Confirm speciální učebny a následné schválení nebo expirace.

**Baseline kódu:** `f34f0ec0f31782ff1886e9b3c4020c2c39a3fe28`.

**Reporting baseline A/C:** tag `c08-report-2026-10-30-v4`, stav k 30. 10. 2026. Kód se od uvedených testů nezměnil; výsledky jsou stručně zapsané přímo v části A.

### 1. Scope & accepted behavior — co systém dělá

Náš systém rezervuje učebny. Student nebo vyučující vytváří a spravuje vlastní rezervace. Správce schvaluje speciální prostory. Aplikace řeší rezervace a dostupnost; ověření identity a doručování oznámení jsou externí hranice. V prototypu identitu zastupuje hlavička X-User-Id a oznámení se logují.

Vybranou operací je **OP-03 Confirm**. Uživatel musí být vlastníkem návrhu DRAFT a začátek musí být v budoucnosti. U běžné učebny úspěšné potvrzení vytvoří CONFIRMED. Změna C02 přidala speciální učebny: jejich potvrzení vytvoří pouze PENDING_APPROVAL. O skutečném přidělení rozhodne správce při OP-05 Approve, který znovu ověří čas, kapacitu a dostupnost.

**Invariant BR-02:** dvě potvrzené rezervace stejné učebny se nesmějí časově překrývat. Čekající žádost učebnu neblokuje. Navazující intervaly nejsou kolize.

```text
Běžná učebna:    DRAFT → CONFIRMED
Speciální:      DRAFT → PENDING_APPROVAL → CONFIRMED / REJECTED / EXPIRED
Zrušení:        DRAFT / PENDING_APPROVAL / CONFIRMED → CANCELLED
                (podle časových pravidel BR-04)
```

Evidence: [specifikace v0.2 — OP-03, OP-05, BR-02/04/08/09](specification-v0.2.md), [úplný stavový diagram](diagrams/state-v0.2.png).

### 2. Architecture — proč je systém takto rozdělený

Hlavní driver: schválení a expirace mění stejnou žádost. Po dosažení začátku už nesmí schválení uspět, ani při souběhu operací.

**Původní AS-IS před C03:** timer a obal operací spouštěly expiraci přímo přes repository. **Implementované TO-BE:** oba žádají o expiraci přes ReservationUseCase; její řízení vlastní ReservationService. Technické spouštění zůstává timeru a SQL zápis persistence adaptéru.

Statický pohled — kdo za co odpovídá:

```text
ReservationController → ReservationUseCase → ReservationService
ApprovalExpiry        → ReservationUseCase       │
                                                ├→ Reservation (stav a pravidla)
                                                ├→ repository porty → JPA → PostgreSQL
                                                └→ NotificationPort → logovací adaptér
BeanConfiguration: transakční obal a předání oznámení po commitu.
```

Dynamický pohled — průběh schválení:

```text
Požadavek správce → expirace ve vlastní transakci
→ transakce schválení: oprávnění → zámek rezervace → kontroly → uložení
→ commit → pokus o oznámení → odpověď
```

**ADR-007:** zvolili jsme společné řízení schválení a expirace v jedné aplikaci. Alternativou byl samostatný expirační worker. Společná aplikace stačí pro současný rozsah a nepotřebuje další nasazovaný proces. Nevýhoda: při výpadku aplikace se zápis EXPIRED odloží; pozdní rozhodnutí ale stále odmítá kontrola času. Samostatný worker bychom znovu zvážili, pokud by expirace musela běžet i během odstávky aplikace.

Evidence: [architektura — A7, E1–E3, ADR-007, G2, H1](architecture-and-decisions.md), [ApprovalExpiry](../src/src/main/java/cz/vsb/reservation/infrastructure/config/ApprovalExpiry.java).

### 3. Detailed Design — jak probíhá konkrétní rozhodnutí

Vybraná jednotka je **schvalování a expirace rezervací**. Metoda `approveReservation(Long id, String userId)` přijímá ID rezervace a správce, vrací aktualizovanou Reservation. Úspěch vyžaduje existující PENDING_APPROVAL, oprávněného správce, čas před začátkem, dostatečnou kapacitu a žádnou kolizi. Výsledkem je uložené CONFIRMED a pokus o oznámení po commitu.

Algoritmus odpovídající aplikační cestě v kódu:

```text
1. Prošlé čekající žádosti expirují v samostatné transakci.
2. Transakce rozhodnutí ověří správce a načte rezervaci pod zámkem.
3. Služba načte učebnu, ověří PENDING_APPROVAL a nepřítomnost kolize.
4. Reservation.approve ověří now < start a kapacitu; změní stav na CONFIRMED.
5. Transakce uloží změnu, provede commit a potom předá oznámení.
```

Zámek řádku chrání souběžné změny stejné rezervace. Databázový EXCLUDE constraint chrání proti překryvu různých rezervací. Odmítnuté schválení nic nepotvrdí; již provedená expirace zůstane uložená. Selhání oznámení nevrací schválení zpět.

| Větev | Výsledek |
|---|---|
| Všechny podmínky splněny | 200, CONFIRMED |
| Chybí identita / uživatel není správce | 401 / 403 |
| Rezervace nebo učebna neexistuje | 404 |
| Nesprávný stav, kolize, nadkapacita nebo prošlý čas | 409 |

**Hranice:** sekundu před začátkem lze schválit, přesně při začátku již ne. Relevantní unit test `approve_confirmsPending_untilOneSecondBeforeStart` obsahuje:

```java
Reservation r = existing(30, PENDING_APPROVAL);
r.approve(resource, START.minusSeconds(1));
assertEquals(CONFIRMED, r.getState());
```

Druhý test `approve_rejectsAtStartAndOverCapacity_keepingPending` ověřuje odmítnutí přesně při začátku i překročení kapacity. Samotný doménový objekt při odmítnutí zůstává PENDING_APPROVAL; změnu na EXPIRED zajišťuje samostatná expirace v aplikační cestě. Oba testy prošly v sadě ReservationTest.

Evidence: [C05](c05_detailed_design.md), [BeanConfiguration](../src/src/main/java/cz/vsb/reservation/infrastructure/config/BeanConfiguration.java), [ReservationService](../src/src/main/java/cz/vsb/reservation/domain/service/ReservationService.java), [Reservation](../src/src/main/java/cz/vsb/reservation/domain/model/Reservation.java), [ReservationTest](../src/src/test/java/cz/vsb/reservation/domain/ReservationTest.java).

### 4. Verification & Evidence — čím to dokazujeme

Ověření proběhlo na uvedené baseline kódu. Očekávání vycházejí ze specifikace REQ-14/REQ-16 a BR-09. Oracle, tedy podle čeho rozhodujeme o správnosti, tvoří konkrétní očekávané HTTP odpovědi, uložený stav v DB a dostupnost učebny. Pouhé přijetí odpovědi 200 by nestačilo.

Pozitivní případ je schválení sekundu před začátkem; hraniční případ je odmítnutí přesně při začátku. Oba prošly. Celkem prošlo 97 testů ve dvou bězích.

**C04:** kritérium propojuje vybranou kvalitu s měřením a navazujícím rozhodnutím. Konkrétní kritérium a úplný řetězec C04 zůstávají otevřenou položkou F1.

### 5. C06 Findings & Engineering Decision — co zjistil druhý tým

Druhý tým zjistil F1: chybí úplný řetězec C04 Quality Concern → Criterion → Measurement → Decision. Statické porovnání dokumentace na původní assessed baseline `c0642a7f74a10819649aa694ccd28cb18533071f` potvrdilo chybějící propojení.

**Disposition: ACCEPTED. Stav: OPEN.** Otevřená akce: doplnění C04 a důkazu uzavření.

### Dva ověřovací příklady

[ApprovalWorkflowApiTest](../src/src/test/java/cz/vsb/reservation/infrastructure/web/ApprovalWorkflowApiTest.java) používá řízený čas UTC a skutečnou PostgreSQL přes Testcontainers. Periodický timer je vypnutý; expirace před rozhodnutím zůstává aktivní. Očekávání vycházejí z REQ-14, REQ-16 a BR-09.

V obou příkladech jde o čekající žádost pro schvalovanou učebnu, rozhoduje `admin`, kapacita vyhovuje a interval nekoliduje. Začátek je `2026-12-01T10:00:00Z`; v pozitivním příkladu je čas nastaven na `09:59:59Z`, v hraničním na `10:00:00Z` téhož dne.

| Příklad / test | Očekávaný výsledek | Aktuální výsledek |
|---|---|---|
| Schválení sekundu před začátkem: `delayedApproval_doesNotBlock_untilApproved` | Před schválením je učebna volná. Správce dostane 200, DB obsahuje CONFIRMED a učebna je obsazená. Opakování vrátí 409. | **PASS**, 30. 10. 2026; odpovědi, stav DB i dostupnost odpovídají očekávání. |
| Přesně při začátku: `exactStart_expiresAndCannotBeApprovedOrCancelled` | Schválení vrátí 409, DB obsahuje EXPIRED, zamítnutí i zrušení vrátí 409 a učebna zůstane volná. | **PASS**, 30. 10. 2026; odpovědi, stav DB i dostupnost odpovídají očekávání. |

**Výsledky ověření — 30. 10. 2026:** kód `f34f0ec0f31782ff1886e9b3c4020c2c39a3fe28`, Windows, JDK 21.0.10, Maven Wrapper. Integrační testy použily Docker 29.8.0 a PostgreSQL 16 přes Testcontainers.

| Testovací sada | Počet | Výsledek |
|---|---|---|
| ReservationTest | 27 | PASS |
| ReservationServiceTest | 24 | PASS |
| ArchitectureTest | 6 | PASS |
| ApprovalWorkflowApiTest | 18 | PASS |
| ReservationLifecycleApiTest | 22 | PASS |
| **Celkem ve dvou bězích** | **97** | **0 selhání, 0 chyb, 0 přeskočení; BUILD SUCCESS** |

Oba příklady výše prošly. Souběžné testy `concurrentConflictingConfirmations_onlyOneAllocation` a `concurrentConflictingApprovals_onlyOneAllocation` také prošly: z dvojice kolidujících požadavků jeden vrátil 200, druhý 409 a v databázi byla právě jedna CONFIRMED rezervace. Jde o 97 vybraných testů, nikoli celou testovací sadu projektu.

Příkazy pro zopakování ze složky `src`:

```powershell
.\mvnw.cmd '-Dtest=ReservationTest,ReservationServiceTest,ArchitectureTest' test
.\mvnw.cmd '-Dtest=ApprovalWorkflowApiTest,ReservationLifecycleApiTest' test
```
**Omezení:** identita je simulována hlavičkou X-User-Id, oznámení se logují a skutečný restart aplikace nebyl samostatně ověřen. C04 zůstává OPEN.

## Registr rizik — C07/C08

Stav k 30. 10. 2026. L = pravděpodobnost, I = dopad (1 nízké, 2 střední, 3 vysoké). Priorita podle L × I: 1–2 LOW, 3–4 MEDIUM, 6–9 HIGH.

### R-01

- **Popis rizika:** Změníme chování rezervací → zapomeneme upravit dokumentaci → popis projektu nebude odpovídat kódu.
- **Kategorie:** Scope / Requirements — shoda dokumentace a řešení.
- **Počáteční riziko (L/I/priorita/důvod):** L=2, I=2, MEDIUM (4). Potvrzování popisuje specifikace i podrobný návrh; při změně může některý popis zůstat starý a způsobit nejasnosti při hodnocení.
- **Strategie ošetření a zdůvodnění:** MITIGATE — porovnat popis s kódem a odstranit rozdíly.
- **Opatření, vlastník, termín:** Zkontrolovat, že aktuální specifikace a C05 popisují stejné potvrzování a rušení jako kód. Jakub Juchelka, 30. 10. 2026.
- **Důkaz účinnosti a stav ověření:** NOT VERIFIED — úplná kontrola shody aktuální specifikace, C05 a kódu zatím není doložena.
- **Očekávané reziduální riziko:** L=1, I=2, LOW (2) po kontrole a opravě případných rozdílů.
- **Skutečné reziduální riziko / NOT VERIFIED:** NOT VERIFIED.
- **Přijetí reziduálního rizika, vlastník, zdůvodnění:** Přijato; Jakub Juchelka. Riziko přijímáme s kontrolou dokumentace při změně chování.
- **Monitoring, trigger, contingency, další review:** Sledovat změny chování a dokumentace. Trigger: změna pravidla nebo nalezený rozpor s kódem. Contingency: určit platné chování podle přijaté specifikace a opravit odpovídající dokument nebo kód. Další review: 6. 11. 2026.

### R-02

- **Popis rizika:** Úkol má svého vlastníka → člen týmu nebude dostupný → jeho práci nebo část obhajoby nebude mít kdo převzít.
- **Kategorie:** People / Resources — dostupnost týmu.
- **Počáteční riziko (L/I/priorita/důvod):** L=1, I=2, LOW (2). Konkrétní absence není doložena; pokud nastane bez zastoupení, zpozdí přípravu.
- **Strategie ošetření a zdůvodnění:** MITIGATE — připravit zastoupení, aby práce nezávisela na jedné osobě.
- **Opatření, vlastník, termín:** Projít společně část A a rozdělit, kdo převezme nepřítomného. Filip Vodák, 30. 10. 2026.
- **Důkaz účinnosti a stav ověření:** NOT VERIFIED — společný průchod a rozdělení zastoupení zatím nejsou doloženy.
- **Očekávané reziduální riziko:** L=1, I=1, LOW (1) po ověření, že zástupce umí část vysvětlit.
- **Skutečné reziduální riziko / NOT VERIFIED:** NOT VERIFIED.
- **Přijetí reziduálního rizika, vlastník, zdůvodnění:** Přijato; Filip Vodák. Nízké počáteční riziko absence přijímáme s plánovaným zastoupením; jeho účinnost zatím není ověřena.
- **Monitoring, trigger, contingency, další review:** Sledovat dostupnost členů. Trigger: člen oznámí, že úkol nebo obhajobu nezvládne. Contingency: práci a výklad převezme domluvený zástupce. Další review: 2. 11. 2026.

### R-03

- **Popis rizika:** Dvě žádosti o stejnou učebnu se stejným časem se potvrzují současně → obě mohou získat stav CONFIRMED → učebna bude přidělená dvakrát.
- **Kategorie:** Technical / Quality — překryv rezervací.
- **Počáteční riziko (L/I/priorita/důvod):** L=2, I=3, HIGH (6). Bez ochrany mohou oba požadavky současně zjistit volnou učebnu; dvojí přidělení porušuje hlavní pravidlo BR-02.
- **Strategie ošetření a zdůvodnění:** MITIGATE — databázová ochrana zabrání dvojímu přidělení a souběžné testy ověří její funkčnost.
- **Opatření, vlastník, termín:** Zachovat databázový zákaz překryvu a ověřit současné Confirm i Approve dvou kolidujících rezervací. Jakub Juchelka, 30. 10. 2026.
- **Důkaz účinnosti a stav ověření:** VERIFIED pro verzi `f34f0ec0f31782ff1886e9b3c4020c2c39a3fe28`: dne 30. 10. 2026 prošly testy `concurrentConflictingConfirmations_onlyOneAllocation` a `concurrentConflictingApprovals_onlyOneAllocation`. Potvrzena byla vždy právě jedna rezervace. Výsledky jsou zapsané v části A.
- **Očekávané reziduální riziko:** L=1, I=3, MEDIUM (3) po ověření ochrany.
- **Skutečné reziduální riziko / NOT VERIFIED:** L=1, I=3, MEDIUM (3). Ochrana je ověřená; dopad případného porušení zůstává vysoký.
- **Přijetí reziduálního rizika, vlastník, zdůvodnění:** Přijato; Jakub Juchelka. Databázová ochrana i souběžné testy fungují na současné verzi.
- **Monitoring, trigger, contingency, další review:** Sledovat výsledky souběžných testů. Trigger: test selže nebo se změní potvrzování či databázová ochrana. Contingency: opravit nebo vrátit změnu a testy zopakovat. Další review: 2. 11. 2026 a po změně potvrzování nebo databázové ochrany.

### R-04

- **Popis rizika:** Práci na úkolu ze cvičení necháme na poslední chvíli → úkol nestihneme dokončit v termínu → odevzdáme neúplný nebo opožděný výstup.
- **Kategorie:** Schedule / Integration — dodržení termínů cvičení.
- **Počáteční riziko (L/I/priorita/důvod):** L=2, I=2, MEDIUM (4). Úkoly navazují na společný projekt a vyžadují rozdělení práce; zpoždění může ovlivnit odevzdání i přípravu na další cvičení.
- **Strategie ošetření a zdůvodnění:** MITIGATE — rozdělení práce a průběžná kontrola umožní odhalit zpoždění včas.
- **Opatření, vlastník, termín:** Po každém cvičení rozdělit úkol mezi členy, určit termín a před odevzdáním zkontrolovat dokončení. Kryštof Lalošák, zavedení postupu 30. 10. 2026; opakovat u každého úkolu.
- **Důkaz účinnosti a stav ověření:** NOT VERIFIED — zatím není doložené včasné dokončení celého úkolu podle tohoto postupu.
- **Očekávané reziduální riziko:** L=1, I=2, LOW (2) po ověření postupu při odevzdání úkolu.
- **Skutečné reziduální riziko / NOT VERIFIED:** NOT VERIFIED.
- **Přijetí reziduálního rizika, vlastník, zdůvodnění:** Přijato; Kryštof Lalošák. Riziko přijímáme s průběžnou kontrolou a možností přerozdělit práci.
- **Monitoring, trigger, contingency, další review:** Sledovat dokončení úkolů vůči termínům. Trigger: některá část práce zaostává za plánem. Contingency: přerozdělit práci a přednostně dokončit povinné části zadání. Další review: před každým odevzdáním.

**Otevřený finding:** F1 — chybějící C04; **ACCEPTED / OPEN**.

## C08 — Project Status Report

Prezentace: [C08_Project_Status.pptx](C08_Project_Status.pptx)

Reporting baseline: `c08-report-2026-10-30-v4`, 30. 10. 2026.
