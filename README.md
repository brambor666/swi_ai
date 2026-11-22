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

Podle [aktuální specifikace v0.3](docs/specification-v0.3.md) se kapacita kontroluje již při vytvoření. Časy jsou v UTC, frontend je převádí. Vytvoření a potvrzení jsou povoleny pouze před začátkem. Zrušení potvrzené rezervace je povoleno nejméně 2 hodiny před začátkem včetně přesné hranice; zrušení návrhu a dotaz na dostupnost nemají časové omezení. Rozhoduje čas serveru při provádění změny. Uživatel vytváří a ruší pouze vlastní rezervace; o potvrzení vlastního návrhu žádá systém, který rozhoduje podle dostupnosti a pravidel. U speciálních prostor žádost čeká na správce. Dostupnost může zjišťovat každý ověřený uživatel. Selhání notifikace nemění výsledek operace a oznámení se zahodí.

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

Otevři http://localhost:8080. Pro demonstraci použij `user-1` a vytvoř budoucí žádost na P1. Pak přepni na `admin`, klikni Načíst čekající žádosti a rozhodni. Migrace V3 se provede automaticky; původní rezervace zachová. Testy: `mvn test` ze složky s pom.xml, se spuštěným Dockerem. Všechny DB testy včetně contextLoads používají izolované Testcontainers.

Změnová analýza: [change-c02-approval.md](docs/change-c02-approval.md). Původní [v0.1](docs/specification-v0.1.md) je zachovaná. Diagramy v0.2 zachycují správce prostor, rozšířený životní cyklus rezervace a průběh potvrzení, schválení a zrušení.

## C08 — potvrzená změna (historický záznam)

[CR-08: maximálně 3 hodiny pro nově vytvářené rezervace](docs/intent-and-change.md#c08--cr-08--maximální-délka-nové-rezervace) je potvrzená změna k implementaci v C09. [Nové testy výchozího stavu pro 3 h a 3 h + 1 s — oba PASS](docs/evidence-and-evolution.md#nové-test-cases-pro-potvrzený-limit-3-h--8-11-2026). Limit 3 hodiny byl potvrzen členy projektového týmu dne 8. 11. 2026. Limit bude stejný pro všechny uživatele a učebny; delší akce se řeší individuálně mimo aplikaci. Implementace patří do C09.

## C09 — build, test a spuštění z čistého checkoutu

CR-08 je implementovaný: nové návrhy mají nejvýše 3 hodiny. Staré delší záznamy zůstávají platné podle původních pravidel. Kontrakt: [specifikace v0.3](docs/specification-v0.3.md); výsledky a stav vydání: [evidence C09](docs/evidence-and-evolution.md#c09--integration-ci-and-release-record).

Požadavky: Git, JDK 21, PowerShell 7 a spuštěný Docker s Linux kontejnery. Maven 3.9.16 stáhne wrapper; první běh vyžaduje přístup k Maven Central a image `postgres:16`. Testy si vytvářejí vlastní PostgreSQL, lokální DB není potřeba.

Po pushi větve `c08-Controlled-Change` druhý člen použije nový adresář a přesný ověřený C09 commit:

```powershell
git clone https://github.com/brambor666/swi_ai.git swi-c09-check
cd swi-c09-check
git checkout 7b1a057f1b6d1c99b4e3e690bcbb227cdcac1625
pwsh -File ./scripts/verify.ps1
```

Skript provede `mvnw clean verify` včetně unit, architektonických a skutečných PostgreSQL integračních testů. Výstupy jsou v `artifacts/c09`: log, Surefire reporty, runtime/Maven, vyřešené verze závislostí, JAR a manifest s commitem, příznakem dirty, exit code a SHA-256. Pro release musí být `dirty=False`. CI spouští stejný skript při push/PR a archivuje tyto soubory. Lokální běh není důkazem běhu CI.

Pro spuštění výsledného JAR a smoke check (z kořene projektu):

```powershell
docker compose -f ./src/src/docker-compose.yml up -d
java -jar ./src/target/reservation-system-0.0.1-SNAPSHOT.jar
# V druhém terminálu:
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/resources -Headers @{'X-User-Id'='user-1'}
```

Očekávání: health `UP`, seznam učeben; na http://localhost:8080 formulář uvádí limit 3 hodin. Aplikaci ukonči Ctrl+C, testovací DB lze zastavit `docker compose -f ./src/src/docker-compose.yml down`. Výchozí lokální DB konfigurace je v Compose a application.properties. Při jiné DB nastav `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`; tajné hodnoty nezapisuj do evidence. Volitelné `RESERVATION_APPROVERS` (default admin), `RESERVATION_EXPIRY_ENABLED` (default true), `SERVER_PORT` (default 8080). Flyway automaticky připraví V1–V3; C09 nemá migraci ani nový parametr.

Nezávislý reproduktor zapíše jméno, datum, commit, OS/JDK/Docker, příkazy, výsledky testů a smoke do evidence. Případné nutné ruční opravy zaznamená; bez skutečného běhu se úspěch neuvádí.

Rollback: spustit artefakt před C09 se stejným schématem V1–V3. Znovu dovolí vytvářet delší rezervace. Data se nemažou ani nevracejí do minulého stavu; žádná datová migrace C09 nevzniká.
