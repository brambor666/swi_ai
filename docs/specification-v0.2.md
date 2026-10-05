# Specifikace základního chování — v0.2

**Projekt:** Rezervační systém učeben
**Verze:** Specification Baseline v0.2
Specifikace rozšiřuje v0.1 o schvalování speciálních prostor podle zadání C02. Dopad změny je popsán ve [změnové kartě](change-c02-approval.md).

## Doménová pravidla a invarianty

- **BR-01 — Interval:** beze změny z v0.1: UTC, povinné start < end, polouzavřený interval [start,end), překryv A.start < B.end a B.start < A.end.
- **BR-02 — Alokace:** beze změny: blokuje pouze CONFIRMED; nikdy dvě kolidující potvrzené rezervace stejné učebny. PENDING_APPROVAL, REJECTED a EXPIRED neblokují. Dostupnost není příslib budoucí alokace.
- **BR-03 — Kapacita:** kladný počet nejvýše kapacita při Create a při skutečné alokaci (Confirm běžné učebny nebo Approve). Správce nemůže kapacitu obejít.
- **BR-04 — Životní cyklus:** Create → DRAFT. Běžná učebna: DRAFT → CONFIRMED. Schvalovaná učebna: DRAFT → PENDING_APPROVAL → CONFIRMED nebo REJECTED nebo EXPIRED. DRAFT lze zrušit kdykoli; PENDING_APPROVAL pouze před začátkem; CONFIRMED při now <= start − 2 h. CANCELLED, REJECTED a EXPIRED jsou konečné. Opakované přechody se odmítají. Create, Confirm a Approve jsou povoleny pouze při now < start. Čas serveru se vyhodnocuje po případném čekání na změnu stejné rezervace.
- **BR-05 — Oprávnění:** vlastník vytváří, odesílá a ruší vlastní rezervace, všichni ověření uživatelé smějí zjišťovat dostupnost a číst své rezervace. Pouze správce prostor smí číst čekající žádosti všech uživatelů, schválit je nebo zamítnout. Role je serverové oprávnění; v prototypu seznam ID z konfigurace, výchozí admin. Identita je lokálně simulována hlavičkou X-User-Id.
- **BR-06 — Odmítnutí a souběh:** rozpoznatelný důvod, odmítnutí samo nic nemění. Změny jedné rezervace odpovídají postupnému pořadí, dvojí rozhodnutí nejvýše jeden úspěch. Approve před Cancel může vést ke dvěma úspěchům a CANCELLED, dovoluje-li BR-04 zrušení CONFIRMED; jinak druhá operace odmítnuta. Cancel/Reject/Expire před Approve zakáže potvrzení. Dvě kolidující Approve nejvýše jedna alokace. Expirace je samostatný časový přechod, může nastat i když je pozdější požadavek odmítnut.
- **BR-07 — Oznámení:** pokus po úspěšném commitu skutečného CONFIRMED nebo CANCELLED, při selhání zahozeno bez opakování; změna a odpověď zůstávají úspěšné. Odeslání do PENDING_APPROVAL, zamítnutí a expirace oznámení potvrzení nevyvolávají. Doručení a pořadí bez garance.
- **BR-08 — Schvalované prostory:** učebna má requiresApproval. Běžné učebny se potvrzují automaticky; speciální prostory, v ukázkových datech přednáškový sál P1, vyžadují rozhodnutí správce. Odeslání žádosti nealokuje a nekontroluje kolize. Při schválení se znovu kontrolují existence učebny, kapacita, čas a kolize. Konflikt ponechá žádost PENDING_APPROVAL; správce může zamítnout nebo rozhodnout později, když konflikt pomine.
- **BR-09 — Platnost žádosti:** každá PENDING_APPROVAL přestává být způsobilá k rozhodnutí při now >= start, včetně přesné hranice, a přejde do EXPIRED. Termín je přímo začátek rezervace, bez další lhůty. Proces je perzistentní a nezávisí na otevřeném prohlížeči. Uložený stav se aktualizuje periodicky a při čtení seznamů / rozhodovacích operacích. Periodické provedení může mít technické zpoždění; ani v takovém případě nesmí pozdní Approve/Reject/Cancel uspět. Po restartu se prošlé žádosti vyhodnotí z uložených časů.

## Pozorovatelné požadavky

REQ-01–04 a REQ-09–12 jsou převzaty z v0.1 s rozšířením přípustných stavů podle BR-04. REQ-05 nově rozlišuje automatické CONFIRMED a PENDING_APPROVAL; REQ-06 a REQ-07 platí pro každý skutečný zápis alokace; REQ-08 až po skutečném CONFIRMED, včetně Approve.

- **REQ-13:** Confirm schvalované učebny převede vlastní platný DRAFT do PENDING_APPROVAL a vrátí ID a stav bez blokování a bez oznámení potvrzení.
- **REQ-14:** oprávněný správce potvrdí pouze platnou PENDING_APPROVAL při splnění kapacity a absence kolize; jinak rozpoznatelné odmítnutí, žádost zůstane čekající, pokud nezávisle nevypršela.
- **REQ-15:** oprávněný správce může platnou PENDING_APPROVAL zamítnout do REJECTED; opakování a rozhodnutí konečného stavu se odmítnou.
- **REQ-16:** při dosažení začátku PENDING_APPROVAL vyprší do EXPIRED a nelze ji potvrdit, zamítnout ani zrušit; dostupnost neblokuje.
- **REQ-17:** vlastník může před začátkem zrušit PENDING_APPROVAL; záznam i ostatní údaje zůstávají a následné schválení se odmítne.
- **REQ-18:** pouze správce může získat seznam aktuálních čekajících žádostí, včetně ID, vlastníka, učebny, intervalu a počtu účastníků; uživatel čte vlastní rezervace ve všech stavech.

## OP-01 — Create Reservation

**Cíl / hodnota:** Zaznamenat záměr bez alokace.

**Spouštěcí událost:** Vlastník zadá učebnu, interval a počet účastníků.

**Pozorovatelné požadavky / pravidla:** REQ-01–02; BR-01, BR-03–06.

**Předpoklady:** Existující učebna, vlastní ověřené ID, kladný počet v kapacitě, platný interval a now < start.

**Stav po úspěchu:** Jeden uložený DRAFT, vrácené ID a stav; dostupnost nezměněna.

**Změna stavu:** [neexistuje] → DRAFT

**Hlavní úspěšný scénář:**

1. Ověřit identitu, učebnu, interval, kapacitu a čas.
2. Uložit DRAFT a vrátit ID a stav.

**Alternativní / chybové výsledky:** Neplatný vstup 400, bez identity 401, cizí vlastník 403, neznámá učebna 404, kapacita nebo čas 409; bez vytvoření záznamu. Kolize nebrání vytvoření.

**Příklady ověření:** Platný požadavek → 201 DRAFT; sekundu před startem povolen, přesně na startu odmítnut; počet nad kapacitu odmítnut.

**Zdůvodnění / zdroj:** Create ani u speciálních učeben není alokace; beze změny.

## OP-02 — Check Availability

**Cíl / hodnota:** Zjistit aktuální obsazenost.

**Spouštěcí událost:** Ověřený uživatel zadá učebnu a interval.

**Pozorovatelné požadavky / pravidla:** REQ-03–04; BR-01–02, BR-05–06.

**Předpoklady:** Existující učebna, platný interval, ověřený uživatel.

**Stav po úspěchu:** Vráceno available, žádná změna rezervací ani alokací.

**Změna stavu:** Žádná

**Hlavní úspěšný scénář:**

1. Ověřit identitu a vstupy.
2. Vyhodnotit překryvy CONFIRMED a vrátit výsledek.

**Alternativní / chybové výsledky:** Neplatný interval 400, bez identity 401, neznámá učebna 404. UNAVAILABLE je úspěšný výsledek, nikoli chyba.

**Příklady ověření:** CONFIRMED [10,11): [9,10) volné, [10:30,11:30) obsazené, [11,12) volné; PENDING_APPROVAL/REJECTED/EXPIRED volné.

**Zdůvodnění / zdroj:** Čekání není závazná alokace; chování beze změny.

## OP-03 — Confirm Reservation

**Cíl / hodnota:** Získat běžnou učebnu nebo odeslat žádost o speciální prostor.

**Spouštěcí událost:** Vlastník požádá o potvrzení svého DRAFT.

**Pozorovatelné požadavky / pravidla:** REQ-05–08, REQ-13; BR-01–08.

**Předpoklady:** Existující vlastní DRAFT a učebna, now < start.

**Stav po úspěchu:** Běžná učebna: CONFIRMED při splnění kapacity a dostupnosti, pokus o oznámení. Speciální: PENDING_APPROVAL bez alokace a bez oznámení potvrzení.

**Změna stavu:** DRAFT → CONFIRMED nebo PENDING_APPROVAL

**Hlavní úspěšný scénář:**

1. Ověřit vlastníka, existenci, aktuální stav a čas.
2. Pro speciální učebnu uložit PENDING_APPROVAL.
3. Pro běžnou znovu ověřit kapacitu a kolizi, uložit CONFIRMED, pokus o oznámení.
4. Vrátit ID a dosažený stav.

**Alternativní / chybové výsledky:** 401/403/404 podle příčiny; nepovolený stav, čas, kapacita nebo konflikt běžné alokace 409. Kolize speciální žádosti nebrání odeslání.

**Příklady ověření:** Běžná učebna → CONFIRMED; sál → PENDING_APPROVAL a dostupnost nadále volná; cizí žádost nebo druhé Confirm odmítnuto.

**Zdůvodnění / zdroj:** Pouze speciální prostory mění průběh potvrzení.

## OP-04 — Cancel Reservation

**Cíl / hodnota:** Odvolat záměr nebo uvolnit alokaci se zachováním záznamu.

**Spouštěcí událost:** Vlastník požádá o zrušení.

**Pozorovatelné požadavky / pravidla:** REQ-09–12, REQ-17; BR-02, BR-04–07, BR-09.

**Předpoklady:** Vlastní DRAFT kdykoli, platný PENDING_APPROVAL před startem, nebo CONFIRMED při now <= start − 2 h.

**Stav po úspěchu:** CANCELLED, údaje zachovány, neblokuje, pokus o oznámení.

**Změna stavu:** DRAFT/PENDING_APPROVAL/CONFIRMED → CANCELLED

**Hlavní úspěšný scénář:**

1. Ověřit identitu a načíst aktuální stav po čekání.
2. Ověřit pravidlo času pro zdrojový stav.
3. Uložit CANCELLED, pokus o oznámení, vrátit ID a stav.

**Alternativní / chybové výsledky:** 401/403/404; konečný stav nebo nesplněný čas 409. Pokud pending dosáhl začátku, nezávisle přejde do EXPIRED a Cancel je odmítnut.

**Příklady ověření:** Pending 09:59:59 pro start 10 → CANCELLED; přesně v 10 → EXPIRED a odmítnutí; Confirmed přesně start − 2 h povolen.

**Zdůvodnění / zdroj:** Rozšířeno o odvolání čekající žádosti; staré politiky zachovány.

## OP-05 — Approve Reservation

**Cíl / hodnota:** Přijmout žádost o speciální prostor jako závaznou alokaci.

**Spouštěcí událost:** Správce prostor zvolí Schválit u čekající žádosti.

**Pozorovatelné požadavky / pravidla:** REQ-06–08, REQ-14; BR-01–03, BR-05–09.

**Předpoklady:** Ověřený správce, existující PENDING_APPROVAL, now < start; učebna existuje, kapacita stačí a interval nekoliduje.

**Stav po úspěchu:** CONFIRMED, alokace platná a invariant zachován, pokus o oznámení.

**Změna stavu:** PENDING_APPROVAL → CONFIRMED

**Hlavní úspěšný scénář:**

1. Ověřit správce.
2. Načíst rezervaci pod zámkem a znovu ověřit stav, čas, učebnu, kapacitu a dostupnost.
3. Atomicky uložit CONFIRMED při zachování BR-02/06.
4. Po commitu pokus o oznámení a vrácení ID a stavu.

**Alternativní / chybové výsledky:** Bez identity 401, nesprávce 403, neznámá rezervace/učebna 404. Nesprávný stav, kapacita, kolize nebo pozdní schválení 409; kolize ponechá čekající žádost. Vypršení probíhá nezávisle.

**Příklady ověření:** Opožděné schválení před startem uspěje; nová kolize nebo snížená kapacita odmítnuta; přesně na startu EXPIRED; Cancel/Reject před Approve znemožní potvrzení; dvojí Approve nejvýše jeden úspěch.

**Zdůvodnění / zdroj:** Zadání požaduje rozhodnutí oprávněné osoby; nejde o druhé uživatelské Confirm.

## OP-06 — Reject Reservation

**Cíl / hodnota:** Uzavřít nevyhovující žádost bez alokace.

**Spouštěcí událost:** Správce prostor zvolí Zamítnout.

**Pozorovatelné požadavky / pravidla:** REQ-15; BR-02, BR-04–06, BR-09.

**Předpoklady:** Ověřený správce, existující PENDING_APPROVAL, now < start.

**Stav po úspěchu:** REJECTED, záznam zachován, učebna neblokována, bez oznámení potvrzení.

**Změna stavu:** PENDING_APPROVAL → REJECTED

**Hlavní úspěšný scénář:**

1. Ověřit správce a aktuální stav pod zámkem.
2. Ověřit platnost žádosti.
3. Uložit REJECTED a vrátit ID a stav.

**Alternativní / chybové výsledky:** 401/403/404; konečný nebo jiný stav a vypršení 409.

**Příklady ověření:** Platná žádost → REJECTED; nesprávce odmítnut; opakování a pozdější Approve odmítnuto.

**Zdůvodnění / zdroj:** Zamítnutí je vyžadováno změnou C02; zdůvodnění zamítnutí není v tomto minimálním rozsahu povinné.

## OP-07 — List Pending Approvals

**Cíl / hodnota:** Umožnit správci najít čekající žádosti.

**Spouštěcí událost:** Správce načte seznam.

**Pozorovatelné požadavky / pravidla:** REQ-18; BR-05, BR-09.

**Předpoklady:** Ověřený správce.

**Stav po úspěchu:** Seznam pouze aktuálních PENDING_APPROVAL; prázdný seznam je úspěch.

**Změna stavu:** Dotaz nemění alokaci; samostatná expirace aktualizuje prošlé stavy.

**Hlavní úspěšný scénář:**

1. Ověřit oprávnění správce.
2. Vyhodnotit expiraci a vrátit čekající žádosti seřazené podle začátku.

**Alternativní / chybové výsledky:** Bez identity 401, nesprávce 403.

**Příklady ověření:** Správce vidí cizí čekající žádost; běžný uživatel odmítnut; prošlá žádost v seznamu není.

**Zdůvodnění / zdroj:** Nový aktér potřebuje najít žádosti bez znalosti jejich ID.

## Kontrola přijetí a konzistence

REQ-01–12 byly revidovány ve v0.1; dopad a změny jsou explicitní výše. REQ-13–18: význam je vymezen zdrojovým stavem, oprávněním a hranicí now < start. Potřeba plyne ze zadání schvalování a odvolání žádosti. Výsledky jsou pozorovatelné v API a uloženém stavu; ověření je uvedeno v operacích a ApprovalWorkflowApiTest. Čekání, zamítnutí a expirace nealokují, všechny alokace používají BR-02 a BR-03. Rozhodnutí lze provést postupně a souběh musí odpovídat takovému pořadí. Požadavky jsou společně proveditelné a nepředepisují databázi ani konkrétní timer. Technická frekvence expirace není business lhůta.

## Předpoklady, neznámé a omezení

Lokálně ověřenou identitu simuluje X-User-Id a správce serverová konfigurace. Skutečné ověření identity a Notification Service nejsou integrovány. Synchronizace pevných rozvrhů zůstává neznámá z C01. Správa učeben a změna requiresApproval nejsou nové uživatelské operace, jde o konfiguraci. Změna příznaku neruší existující CONFIRMED ani neobchází schválení už čekajících žádostí. Zbývá aktualizovat diagramy pro v0.2 a doplnit diagramy aktivit pro Create a Check Availability.

## Drivery pro C03

Perzistence čekajícího procesu a obnova po restartu, nezávislé vyhodnocování expirace, izolace autorizační politiky, bezpečný souběh Approve/Reject/Cancel/Expire a oznámení až po commitu. Implementační rozhodnutí jsou v architecture-and-decisions.md, výsledky běhu v evidence-and-evolution.md.


## OP-08 — Expire Pending Approval (automatické vypršení)

**Cíl / hodnota:** ukončit žádost, o níž už nelze včas rozhodnout.
**Spouštěcí událost:** čas serveru dosáhne start čekající žádosti; periodická kontrola nebo vyhodnocení před operací.
**Pozorovatelný požadavek:** REQ-16, BR-04, BR-06, BR-09.
**Předpoklady:** uložená PENDING_APPROVAL a now >= start.
**Stav po úspěchu:** EXPIRED, údaje zachované, bez alokace nebo oznámení potvrzení.
**Změna stavu:** PENDING_APPROVAL → EXPIRED.

**Hlavní scénář:**

1. Systém porovná uložený začátek s časem serveru.
2. Změní pouze stále čekající prošlé žádosti.
3. Následující čtení vrátí EXPIRED, pozdější rozhodnutí je odmítnuto.

**Alternativy:** před startem beze změny; již rozhodnutá nebo zrušená rezervace se nemění. Při nedostupnosti databáze se uložení odloží do další kontroly; rozhodnutí stále musí ověřit aktuální čas. Souběh s Approve/Cancel/Reject odpovídá BR-06; pozdní přepis konečného stavu není dovolen.
**Příklady ověření:** now = start − 1 s zůstává čekající; now = start přejde do EXPIRED; opakovaný sken nic neobnoví; dotaz na dostupnost není blokován. Test používá řízené hodiny a stejný sken jako periodický proces, bez čekání na reálný čas.
**Zdůvodnění / zdroj:** Zadání C02 vyžaduje vypršení žádosti. Hranicí je začátek rezervace, protože po něm už nelze rezervaci potvrdit.
