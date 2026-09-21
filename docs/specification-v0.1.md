# Specifikace základního chování — v0.1

**Projekt:** Rezervační systém učeben  
**Stav:** Textová specifikace po rozhodnutí zadavatele o TBD-01 až TBD-06. Diagramy a ověření aplikace následují samostatně.

Student nebo vyučující rezervuje celou učebnu (Resource). Rezervace obsahuje ID, učebnu, uživatele, začátek, konec, počet účastníků a stav. `DRAFT` je návrh, `CONFIRMED` platná alokace a `CANCELLED` zachovaný záznam zrušené rezervace. `Approve` do v0.1 nepatří. Model C01 nemá aktivní/neaktivní učebny, proto tento atribut z referenčního příkladu nepřebíráme.

## Doménová pravidla a invarianty

- **BR-01 — Interval:** oba časy jsou povinné a `start < end`. Systém přijímá, ukládá a porovnává časové okamžiky v UTC; frontend zajišťuje převod do/z místního času. Interval je `[start,end)`. Překryv nastává právě tehdy, když `A.start < B.end` a `B.start < A.end`. Navazující intervaly se nepřekrývají.
- **BR-02 — Výhradní alokace:** pouze `CONFIRMED` blokuje učebnu. V žádném uloženém stavu nesmějí existovat dvě překrývající se potvrzené rezervace stejné učebny, ani při souběhu. Výsledek dostupnosti platí při vyhodnocení dotazu a nezaručuje budoucí potvrzení.
- **BR-03 — Kapacita:** počet účastníků je kladné celé číslo nejvýše rovné kapacitě učebny. Horní mez se kontroluje již při Create a znovu při Confirm; nadkapacitní návrh se nevytvoří.
- **BR-04 — Životní cyklus a čas:** vytvoření vede do `DRAFT`, potvrzení pouze z `DRAFT` do `CONFIRMED`. Zrušení vede z `DRAFT` nebo `CONFIRMED` do `CANCELLED`. Opakované potvrzení i zrušení se odmítá. Zrušení mění pouze stav a zachovává záznam i ostatní údaje. Create, Confirm a Cancel jsou povoleny pouze při `currentTime <= start − 2 hodiny`. Přesná hranice je povolena; méně než dvě hodiny před začátkem i minulost jsou odmítnuty. Rozhoduje čas serveru v UTC při přijetí změny stavu, nikoli čas odeslání požadavku z frontendu. Dotaz na dostupnost tuto lhůtu nevyžaduje. Samostatná editace rezervace není součástí čtyř operací v0.1.
- **BR-05 — Oprávnění:** identitu ověřuje externí systém. Uživatel vytváří a ruší pouze vlastní rezervace; ID vlastníka musí odpovídat ověřené identitě. Dostupnost může zjišťovat každý ověřený uživatel. O potvrzení vlastního návrhu může uživatel požádat, ale rozhodnutí provádí systém podle pravidel a dostupnosti, bez lidského schvalovatele. Neoprávněná operace se odmítne bez změny dat nebo zpřístupnění chráněného výsledku.
- **BR-06 — Odmítnutí a souběh:** odmítnutí kvůli vstupu, oprávnění, stavu nebo doménovému pravidlu vrací rozpoznatelný důvod a samo nic nezmění. Odpověď popisuje výsledek dané operace, nikoli stav po pozdějších změnách. Souběh změn jedné rezervace odpovídá některému postupnému pořadí: při splnění ostatních podmínek včetně časové hranice může Confirm před Cancel vést ke dvěma úspěchům a konečnému `CANCELLED`; Cancel před Confirm vede k odmítnutí potvrzení a konečnému `CANCELLED`. Pokud během souběhu uplyne lhůta BR-04, pozdější změna se odmítne. Dvě potvrzení téže rezervace nebo dvě její zrušení mohou mít nejvýše jeden úspěch. Pozdní zápis nesmí obnovit zrušenou rezervaci.
- **BR-07 — Oznámení:** po úspěšném potvrzení nebo zrušení, včetně zrušení návrhu, systém provede pokus předat Notification Service typ změny, ID rezervace a jejího uživatele. Při selhání se oznámení zahodí bez opakování; uložený stav ani úspěšný výsledek operace se nemění. Doručení a jeho pořadí nejsou garantovány. Odmítnutý pokus, Create ani Check Availability oznámení úspěšného přechodu nevyvolává.

**Zdroj pravidel:** reference C02, doména C01 a rozhodnutí zadavatele uvedená na konci. V příkladech jsou časy v UTC ve stejný den; pokud není uvedeno jinak, čas serveru je 07:00, uživatel je ověřený vlastník a ostatní podmínky jsou splněny.

## OP-01 — Vytvořit návrh rezervace (Create Reservation)

**Cíl / hodnota pro uživatele:** zaznamenat záměr využít učebnu bez její alokace.

**Spouštěcí událost:** uživatel odešle učebnu, uživatele rezervace, začátek, konec a počet účastníků.

**Pozorovatelné požadavky:**

- **REQ-01:** při splnění předpokladů systém jedním úspěšným provedením vytvoří právě jeden `DRAFT` se zadanými údaji a jedinečným ID a vrátí ID a stav. Automatické rozpoznání opakovaně doručeného požadavku není tímto požadavkem garantováno.
- **REQ-02:** kolize s jinou rezervací vytvoření návrhu nebrání. Neplatný vstup, neexistující učebna nebo nedostatečné oprávnění vede k odmítnutí bez vytvoření záznamu.

**Předpoklady:** existující učebna, platný interval, počet účastníků podle BR-03, časová hranice BR-04 a vlastní rezervace podle BR-05.

**Stav po úspěšném provedení:** nový `DRAFT` je uložen; vytvoření samo nemění dostupnost učebny.

**Změna stavu:** `[neexistuje] → DRAFT`.

**Odkaz na doménová pravidla / invarianty:** BR-01 až BR-06.

**Hlavní úspěšný scénář:**

1. Uživatel odešle údaje.
2. Systém ověří oprávnění, učebnu a vstupy.
3. Uloží `DRAFT` a vrátí jeho ID a stav.

**Alternativní / chybové výsledky:** neznámá učebna, neúplný interval, `start >= end`, neplatný počet či nadkapacita, nesplněná časová hranice nebo vytvoření za jiného uživatele → odmítnutí podle BR-06. Kolize nebrání vzniku návrhu.

**Příklady ověření:**

| Vstup / situace | Očekávaný výsledek |
|---|---|
| U1, kapacita 30, oprávněný uživatel, 20 účastníků, `[10:00,11:00)` | Jeden `DRAFT`, vrácené ID, bez nové alokace |
| `start = end`, chybějící konec nebo neznámá učebna (samostatné případy) | Odmítnutí, žádný nový záznam |
| Prázdné ID; počet 0, −1 nebo 1,5 (samostatné případy) | Odmítnutí, žádný nový záznam |
| Stejný interval již blokuje jiná rezervace | Nový `DRAFT` vznikne |
| 30 / 31 účastníků při kapacitě 30 | 30 → `DRAFT`; 31 → odmítnutí bez záznamu |
| Server 08:00 / 08:00:01, začátek 10:00 | Přesně 2 hodiny → `DRAFT`; méně než 2 hodiny → odmítnutí |
| Začátek v minulosti nebo vlastník odlišný od ověřeného uživatele | Odmítnutí bez záznamu |

**Zdůvodnění / zdroj:** C01 a reference Create; vytvoření zaznamenává záměr, potvrzení teprve alokuje učebnu.


## OP-02 — Ověřit dostupnost učebny (Check Availability)

**Cíl / hodnota pro uživatele:** zjistit časovou dostupnost učebny; dotaz neposuzuje kapacitní vhodnost.

**Spouštěcí událost:** uživatel zadá učebnu a interval.

**Pozorovatelné požadavky:**

- **REQ-03:** pro platný dotaz systém vrátí `UNAVAILABLE`, pokud se interval překrývá s některou `CONFIRMED` rezervací stejné učebny, jinak `AVAILABLE`.
- **REQ-04:** dotaz nemění rezervace ani alokace. Neexistující učebnu, neplatný interval nebo nedostatečné oprávnění odmítne místo výsledku dostupnosti.

**Předpoklady:** existující učebna, platný interval a přístup podle BR-05.

**Stav po úspěšném provedení:** vrácen výsledek; data se dotazem nezmění. `UNAVAILABLE` je platný výsledek, nikoli chyba.

**Změna stavu:** žádná.

**Odkaz na doménová pravidla / invarianty:** BR-01, BR-02, BR-05, BR-06.

**Hlavní úspěšný scénář:**

1. Uživatel odešle dotaz.
2. Systém ověří přístup, učebnu a interval.
3. Vyhodnotí blokující rezervace a vrátí dostupnost.

**Alternativní / chybové výsledky:** neznámá učebna, neúplný interval, `start >= end` nebo nedostatečné oprávnění → odmítnutí. Souběžná změna může ovlivnit dostupnost; dotaz nevytváří příslib potvrzení.

**Příklady ověření:** první čtyři řádky předpokládají jedinou `CONFIRMED` rezervaci U1 `[10:00,11:00)`.

| Dotaz / situace | Očekávaný výsledek |
|---|---|
| U1 `[09:00,10:00)` | `AVAILABLE` |
| U1 `[10:30,11:30)` | `UNAVAILABLE` |
| U1 `[11:00,12:00)` | `AVAILABLE` |
| U1 `[10:00,11:00)` nebo `[10:15,10:45)` | `UNAVAILABLE` |
| Překrývají se pouze `DRAFT`/`CANCELLED` nebo rezervace jiné učebny | `AVAILABLE` |
| Neznámá učebna nebo neplatný interval | Odmítnutí, nikoli `AVAILABLE` |
| Ověřený student nebo vyučující zjišťuje dostupnost | Dotaz povolen bez ohledu na vlastnictví existujících rezervací |
| Neověřený uživatel | Odmítnutí přístupu |

U každého případu ověřit také absenci změn způsobených dotazem.

**Zdůvodnění / zdroj:** reference Availability a výhradní alokace z C01; stejný význam překryvu jako při potvrzení.


## OP-03 — Potvrdit rezervaci (Confirm Reservation)

**Cíl / hodnota pro uživatele:** získat platnou alokaci učebny.

**Spouštěcí událost:** vlastník odešle vlastní návrh k potvrzení; systém rozhodne podle dostupnosti a pravidel bez lidského schválení.

**Pozorovatelné požadavky:**

- **REQ-05:** při splnění předpokladů, kapacity a absence konfliktu systém uloží `CONFIRMED` a vrátí ID a stav dosažený touto operací. Jinak potvrzení odmítne; při souběhu platí BR-06.
- **REQ-06:** při souběžném potvrzování dvou vzájemně se překrývajících návrhů stejné učebny, bez mezilehlého zrušení jejich alokace, uspěje nejvýše jeden. Nekolidující návrhy mohou uspět současně; BR-02 platí vždy.
- **REQ-07:** potvrzení odmítnuté kvůli vstupu, oprávnění, stavu nebo doménovému pravidlu samo nezmění rezervaci ani nevytvoří alokaci; vrátí důvod odmítnutí. Selhání oznámení po uložení není odmítnutím potvrzení (BR-07).
- **REQ-08:** úspěšný přechod vyvolá oznámení podle BR-07.

**Předpoklady:** rezervace a její učebna existují, stav je `DRAFT`, žádost pochází od vlastníka a platí časová hranice BR-04. Předchozí kontrola dostupnosti není nutná ani dostačující.

**Stav po úspěšném provedení:** rezervace je `CONFIRMED`, blokuje učebnu, splňuje kapacitu a invariant překryvů; ostatní údaje zůstávají stejné. Za dostupné notifikační služby je předáno oznámení.

**Změna stavu:** `DRAFT → CONFIRMED`.

**Odkaz na doménová pravidla / invarianty:** BR-01 až BR-07.

**Hlavní úspěšný scénář:**

1. Uživatel odešle ID rezervace.
2. Systém ověří oprávnění, existenci rezervace/učebny a stav.
3. Ověří kapacitu, časovou hranici a konflikt a uloží potvrzení při zachování BR-02/06.
4. Pokusí se předat oznámení a vrátí ID a úspěšný výsledek potvrzení i při selhání notifikace.

**Alternativní / chybové výsledky:** neznámá rezervace/učebna, cizí rezervace, jiný stav než `DRAFT`, nadkapacita, nesplněná časová hranice nebo konflikt → odmítnutí podle BR-06. Bez jiné souběžné změny návrh zůstává `DRAFT`. Chyba oznámení → oznámení zahozeno, potvrzení zůstává úspěšné.

**Příklady ověření:**

| Situace | Očekávaný výsledek |
|---|---|
| `DRAFT`, 30 účastníků, kapacita 30, bez konfliktu | `CONFIRMED`, blokovaná dostupnost, předané oznámení |
| Testem připravený nadkapacitní `DRAFT` (běžné Create jej již odmítá) | Potvrzení odmítnuto, zůstává `DRAFT`, bez oznámení |
| Existuje potvrzená U1 `[10:00,11:00)`, návrh `[10:30,11:30)` | Odmítnutí konfliktu, zůstává `DRAFT` |
| Stejná situace, návrh `[11:00,12:00)` | Potvrzení uspěje |
| Dva jinak platné konfliktní návrhy se potvrzují současně, bez rušení či technické chyby | Jeden uspěje, druhý je odmítnut; nevznikne dvojí alokace |
| Dva nekolidující návrhy se potvrzují současně | Oba při splnění ostatních podmínek uspějí |
| Stav již `CONFIRMED` nebo `CANCELLED` | Odmítnutí, stav nezměněn |
| Po dotazu na volný interval mezitím uspělo jiné konfliktní potvrzení | Odmítnutí konfliktu |
| Server 08:00 / 08:00:01, začátek 10:00 | Potvrzení uspěje / je odmítnuto a zůstává `DRAFT` |
| Žádost o potvrzení cizí rezervace | Odmítnutí bez změny |
| Selhání Notification Service po uložení | Úspěšné potvrzení, `CONFIRMED`, oznámení zahozeno bez opakování |

**Zdůvodnění / zdroj:** reference Confirm, zákaz dvojí alokace a kapacitní pravidlo učeben z C01.


## OP-04 — Zrušit rezervaci (Cancel Reservation)

**Cíl / hodnota pro uživatele:** odvolat návrh nebo uvolnit alokaci se zachováním záznamu.

**Spouštěcí událost:** oprávněný uživatel požádá o zrušení rezervace podle ID.

**Pozorovatelné požadavky:**

- **REQ-09:** při splnění předpokladů systém uloží `CANCELLED` a vrátí ID a stav dosažený touto operací.
- **REQ-10:** zrušená rezervace neblokuje učebnu a její záznam i ostatní údaje zůstávají zachovány.
- **REQ-11:** nepřípustné nebo neoprávněné zrušení se odmítne podle BR-06. Souběžné potvrzení nesmí obnovit již zrušenou rezervaci.
- **REQ-12:** úspěšný přechod vyvolá oznámení podle BR-07.

**Předpoklady:** rezervace existuje, je `DRAFT` nebo `CONFIRMED`, uživatel má oprávnění a zrušení splňuje politiku BR-04.

**Stav po úspěšném provedení:** uložený `CANCELLED` neblokuje učebnu; ostatní údaje zůstávají stejné. Za dostupné notifikační služby je předáno oznámení. Jiná rezervace může dostupnost nadále omezovat.

**Změna stavu:** `DRAFT → CANCELLED` nebo `CONFIRMED → CANCELLED`.

**Odkaz na doménová pravidla / invarianty:** BR-02, BR-04 až BR-07.

**Hlavní úspěšný scénář:**

1. Uživatel odešle ID rezervace.
2. Systém ověří oprávnění, existenci a přípustnost zrušení.
3. Uloží `CANCELLED` při zachování BR-06, pokusí se předat oznámení a vrátí ID a úspěšný výsledek zrušení i při selhání notifikace.

**Alternativní / chybové výsledky:** neznámá či cizí rezervace, již `CANCELLED` nebo nesplněná časová hranice → odmítnutí bez nové změny a oznámení úspěchu. Souběh řeší BR-06. Chyba oznámení → oznámení zahozeno, zrušení zůstává úspěšné.

**Příklady ověření:**

| Situace | Očekávaný výsledek |
|---|---|
| Existující `DRAFT` | `CANCELLED`, zachované údaje, předané oznámení |
| `CONFIRMED`, žádná jiná blokující rezervace v intervalu | `CANCELLED`, interval je dostupný, záznam zachován |
| Opakované zrušení | Odmítnutí, stále `CANCELLED`, bez nového oznámení |
| Neznámé ID | Odmítnutí, žádná změna |
| Server 08:00 / 08:00:01, začátek 10:00 | Zrušení uspěje / je odmítnuto bez změny |
| Zrušení na začátku, po konci nebo cizím uživatelem | Odmítnutí bez změny |
| Selhání Notification Service po uložení | Úspěšné zrušení, `CANCELLED`, oznámení zahozeno bez opakování |
| Souběžné Confirm a Cancel původního `DRAFT`, ostatní podmínky splněny | Konečný `CANCELLED`, odpovědi odpovídají pořadí z BR-06 |

**Zdůvodnění / zdroj:** reference Cancel, životní cyklus z C01 a dvouhodinová hranice přijatá zadavatelem, aby se učebna neuvolňovala na poslední chvíli.

## Uzavřená rozhodnutí zadavatele

| Původní ID | Rozhodnutí |
|---|---|
| TBD-01 | UTC, převod místního času provádí frontend. |
| TBD-02 | Horní mez kapacity kontrolovat již při Create. |
| TBD-03 | Žádné rezervace do minulosti; vytvoření a změny nejméně 2 hodiny před začátkem. Přesná hranice je zahrnuta dle BR-04. |
| TBD-04 | Vytváření a rušení pouze pro sebe, potvrzuje systém podle dostupnosti a pravidel, dostupnost zjišťuje každý ověřený uživatel. |
| TBD-05 | Přijaty výsledky souběhu podle BR-06 při splnění ostatních podmínek. |
| TBD-06 | Při selhání Notification Service oznámení zahodit; rezervace zůstává úspěšně změněna. |

Diagramy a skutečné provedení příkladů budou doplněny zvlášť. Přijetí pravidel neprokazuje jejich implementaci; zejména UTC, časová hranice, oprávnění a kontrola kapacity při Create vyžadují navazující ověření kódu.
