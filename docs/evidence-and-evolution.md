# Důkazy o funkčnosti a evoluce systému (Evidence and Evolution)

Tento dokument definuje, jakými způsoby prokazujeme správnost implementace (Evidence) a slouží jako deník pro zaznamenávání významných změn v kódové základně a testovacích strategiích v čase (Evolution).

## 1. Důkazy o funkčnosti (Evidence)

Abychom garantovali, že systém splňuje všechny business požadavky (zejména zamezení překryvů a kontrolu kapacit), spoléháme na víceúrovňovou strategii testování.

### A. Jednotkové testy (Doménová vrstva)
Nejkritičtější business pravidla sídlí uvnitř doménového modelu a nemají žádné externí závislosti. K jejich ověření používáme **JUnit 5**. 
*   **Co testujeme:** 
    *   Zda nelze potvrdit rezervaci, pokud počet účastníků překročí kapacitu `Resource`.
    *   Zda nelze provést neplatný stavový přechod (např. z `CANCELLED` přímo do `CONFIRMED`).
*   **Cíl:** Blesková zpětná vazba při vývoji (vykonání v řádech milisekund).

### B. Integrační testy (Databázová vrstva a souběh)
Ověření pravidla, že *dvě potvrzené rezervace se nesmějí překrývat*, vyžaduje testování chování databáze při souběžných požadavcích (race conditions).
*   **Jak testujeme:** Pomocí frameworku **Testcontainers** startujeme pro účely testů reálnou instanci PostgreSQL v Dockeru.
*   **Co testujeme:**
    *   Uložení validní rezervace.
    *   Chování systému při pokusu o uložení dvou rezervací na stejnou učebnu a čas ve stejný okamžik (očekáváme `OptimisticLockingFailureException` nebo narušení databázového constraintu).
    *   Správnost mapování doménových objektů na JPA entity.

### C. Architektonické testy (Ochrana Hexagonu)
Pro zabránění postupné degradace architektury využíváme knihovnu **ArchUnit**.
*   **Co testujeme:** 
    *   Třídy v balíčku `domain` nesmějí importovat žádné třídy z balíčků `infrastructure`, `adapter` nebo frameworku `org.springframework`.
    *   Všechny REST controllery musí volat pouze Inbound Porty, nikdy ne napřímo repozitáře.

---

## 2. Pozorovatelnost a metriky (Observability)

Důkazem o správném fungování v produkci jsou reálná data. Aplikace (pomocí Spring Boot Actuator a případně Micrometer) vystavuje následující logy a metriky:
*   **Business metriky:** Počet vytvořených vs. zrušených rezervací, četnost zamítnutých rezervací z důvodu časového překryvu.
*   **Technické metriky:** Úspěšnost odeslání zpráv přes `Notification Service` (Boundary). Zaznamenáváme selhání, pokud externí služba neodpovídá.

---

## 3. Deník evoluce (Evolution Log)

Tato sekce slouží jako chronologický záznam významných zásahů do systému, refaktoringů nebo změn v testovací strategii. Na rozdíl od ADR (která řeší *návrh*), tento deník řeší *realizaci* a *zjištění*.

| Datum | Verze / Fáze | Změna a zjištění | Dopad na systém |
| :--- | :--- | :--- | :--- |
| **Září 2026** | v1.0.0 (Init) | **Založení projektu a infrastruktury.** Nastavení Java 21, Spring Boot, PostgreSQL a Flyway. Implementována základní Hexagonální struktura. | Výchozí stav pro další vývoj. Testcontainers integrovány do Maven build fáze. |
| *(Budoucnost)* | *v1.x* | *(Příklad)* *Zjištěn problém s výkonem při ověřování překryvů u velkého množství rezervací.* | *Přidán GiST index do PostgreSQL nad časovými intervaly rezervací, optimalizován dotaz v `PostgresAdapteru`.* |