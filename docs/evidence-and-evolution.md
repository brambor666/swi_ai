# C01 Engineering Spike

Question / unknown:
Lze rezervaci spolehlivě uložit do skutečné PostgreSQL databáze,
znovu ji načíst a zároveň zabránit překrývajícím se potvrzeným
rezervacím?

What we did:
Implementovali jsme persistence vrstvu pomocí JPA a PostgreSQL,
vytvořili databázové schéma pomocí Flyway a přidali integrační
testy využívající Testcontainers. Testy ověřují uložení a načtení
rezervace a také databázový constraint zabraňující překryvu
potvrzených rezervací. Ověření jsme spustili příkazem mvn test.

Observed result:
PostgreSQL kontejner se úspěšně spustil a Flyway vytvořil
databázové schéma. Všechny čtyři persistence integrační testy
prošly. Po uložení a načtení rezervace byla ověřena shoda ID,
uživatele a stavu DRAFT. Databázový constraint odmítl druhou
překrývající se potvrzenou rezervaci stejné učebny.
Celkem prošlo všech 18 testů bez chyb a bez přeskočení.
Build skončil výsledkem BUILD SUCCESS.

Decision / what changes because of the result:
Budeme nadále používat PostgreSQL pro persistence vrstvu
a databázový constraint jako dodatečnou ochranu proti souběžným
konfliktním rezervacím. Persistence zůstane oddělena od domény
přes repository porty a integrační testy persistence budeme
dál ověřovat pomocí Testcontainers.

## Evidence C02: sjednocení v0.1 a souběh

Baseline: specification-v0.1.md, včetně revize REQ-01 až REQ-12. Tato úprava sjednotila časová pravidla a souběh; schvalování je popsáno samostatně ve v0.2.

Nalezený nesoulad a řešení: implementace a testy vyžadovaly dvě hodiny před začátkem i pro Create, Confirm a Cancel návrhu. Opraveny podle BR-04: Create/Confirm pouze před začátkem, Cancel návrhu bez časového omezení, Cancel potvrzené rezervace včetně hranice start − 2 h. Opraven byl i přehled projektu a text ve frontendu.

Výsledky ověření:
- Kompletní Maven `test`: 90 testů, 0 selhání, 0 chyb, 0 přeskočení, BUILD SUCCESS.
- Po přidání testu vynuceného čekání na zámek spuštěno `-Dtest=ReservationLifecycleApiTest test`: všech 22 testů prošlo bez selhání, chyb a přeskočení. Ostatní testy po této poslední změně nebyly opakovány.
- Create: uložený DRAFT; neplatný vstup, překročení kapacity a začátek v minulosti odmítnuty. Doménový test ověřil úspěch sekundu před začátkem a odmítnutí přesně na začátku.
- Availability: volný i kolidující interval, navazující hranice, odmítnutí neplatného dotazu a absence změn.
- Confirm: úspěšné potvrzení i méně než dvě hodiny před začátkem; odmítnutí konfliktu a potvrzení přesně na začátku.
- Cancel: zachovaný CANCELLED a uvolněná dostupnost; opakování odmítnuto; návrh zrušen i po konci; potvrzená rezervace na dvouhodinové hranici zrušena, po hranici odmítnuta.
- Souběžné HTTP požadavky proti PostgreSQL přes Testcontainers: Confirm/Cancel stejné rezervace před i po dvouhodinové hranici, dvě Confirm stejné rezervace, dvě Cancel stejné rezervace a dvě konfliktní Confirm různých rezervací. Pozdní potvrzení neobnovilo CANCELLED; opakované přechody měly nejvýše jeden úspěch; konfliktní alokace právě jednu potvrzenou rezervaci.
- Vynucené čekání: necommitnutý zápis CANCELLED držel řádek, test ověřil skutečné čekání Confirm na databázový zámek. Po commitu zrušení Confirm vrátil 409 / INVALID_STATE, konečný stav zůstal CANCELLED.

Architektonický driver: atomická změna životního cyklu a vyhodnocení aktuálního stavu/času po čekání. Řešení dokumentuje ADR-005; oznámení se předává až po commitu.

Zbývající předpoklady: externě ověřená identita je při lokálním běhu simulována hlavičkou; synchronizace rozvrhů zůstává neznámá. Schvalovací proces je popsán v následující části.

Commit / tag aplikace: úpravy zatím nejsou commitnuté; výchozí HEAD je 3265aed. Před odevzdáním doplnit commit zahrnující tyto změny.


## Evidence C02: schvalovací změna v0.2

Baseline: specification-v0.2.md. Původní v0.1 zůstává zachovaná. Analýza dopadu je v change-c02-approval.md. Aktualizace diagramů zatím není hotová.

Shrnutí dopadu: Create a časová dostupnost beze změny. Confirm speciálního prostoru vytvoří PENDING_APPROVAL. Nový správce schvaluje / zamítá; Approve znovu ověřuje čas, kapacitu a konflikt. Pending lze zrušit před začátkem a při začátku vyprší. Pouze CONFIRMED blokuje. Ukázkový P1 vyžaduje schválení, běžné A1/B2 nikoli. Migrace V3 přidává příznak a stavy bez změny uložených rezervací.

Výsledky ověření:
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

Nalezené nesoulady a řešení: přidán explicitní zákaz Confirm z jiného stavu než DRAFT, aby nově povolený přechod pending → confirmed nemohl obejít správce. Timer v testech vypnut a stejný sken volán explicitně s řízenými hodinami, aby cached Spring kontext nevolal již ukončené testovací kontejnery. V běžné aplikaci timer zůstává zapnut.

Drivery pro C03: perzistentní asynchronní proces, obnova časového stavu, autorizace správce, atomický souběh rozhodnutí/rušení/expirace, oznámení po commitu. Rozhodnutí viz ADR-005 a ADR-006.

Zbývající předpoklady / neznámé: lokální X-User-Id simuluje skutečnou identitu; oznámení pouze loguje; synchronizace rozvrhů není rozhodnutá. Zbývá aktualizace diagramů.

Commit / tag aplikace: změny jsou v pracovním stromu nad výchozím HEAD 3265aed, nebyly commitnuté. Před zmrazením CP1 je potřeba zaznamenat skutečný commit těchto změn. Test reporty jsou lokálně v src/target/surefire-reports; souhrn v src/target/v02-verification.log a v02-final-check.log.
