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
