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