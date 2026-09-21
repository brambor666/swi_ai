# Specifikace základního chování — v0.1

**Projekt:** Rezervační systém učeben  
**Stav:** Návrh k týmovému přijetí po textové revizi; otevřená rozhodnutí jsou na konci.

Student nebo vyučující rezervuje celou učebnu (Resource). Rezervace obsahuje ID, učebnu, uživatele, začátek, konec, počet účastníků a stav. `DRAFT` je návrh, `CONFIRMED` platná alokace a `CANCELLED` zachovaný záznam zrušené rezervace. `Approve` do v0.1 nepatří. Model C01 nemá aktivní/neaktivní učebny, proto tento atribut z referenčního příkladu nepřebíráme.

## Doménová pravidla a invarianty

- **BR-01 — Interval:** oba časy jsou povinné a `start < end`. Interval je `[start,end)`. Překryv nastává právě tehdy, když `A.start < B.end` a `B.start < A.end`. Navazující intervaly se nepřekrývají. Časová interpretace: TBD-01.
- **BR-02 — Výhradní alokace:** pouze `CONFIRMED` blokuje učebnu. V žádném uloženém stavu nesmějí existovat dvě překrývající se potvrzené rezervace stejné učebny, ani při souběhu. Výsledek dostupnosti platí při vyhodnocení dotazu a nezaručuje budoucí potvrzení.
- **BR-03 — Kapacita:** počet účastníků je kladné celé číslo. Potvrzená rezervace nesmí překročit kapacitu; rovnost je povolena. Návrh připouští nadkapacitní `DRAFT` (TBD-02).
- **BR-04 — Životní cyklus:** vytvoření vede do `DRAFT`, potvrzení pouze z `DRAFT` do `CONFIRMED`. Zrušení vede z `DRAFT` nebo `CONFIRMED` do `CANCELLED`. Opakované potvrzení i zrušení se odmítá. Zrušení mění pouze stav a zachovává záznam i ostatní údaje. Navržená politika neomezuje vytvoření, potvrzení ani zrušení vůči aktuálnímu času (TBD-03).
- **BR-05 — Oprávnění:** ID uživatele je povinné a neprázdné. Podle C01 identitu ověřuje externí systém; zadání ID samo nestačí. Neoprávněná operace se odmítne bez změny dat nebo zpřístupnění chráněného výsledku. Konkrétní oprávnění: TBD-04.
- **BR-06 — Odmítnutí a souběh:** odmítnutí kvůli vstupu, oprávnění, stavu nebo doménovému pravidlu vrací rozpoznatelný důvod a samo nic nezmění. Odpověď popisuje výsledek dané operace, nikoli stav po pozdějších změnách. Navržený souběh změn jedné rezervace odpovídá některému postupnému pořadí (TBD-05): Confirm před Cancel může vést ke dvěma úspěchům a konečnému `CANCELLED`; Cancel před Confirm vede k odmítnutí potvrzení a konečnému `CANCELLED`. Dvě potvrzení téže rezervace nebo dvě její zrušení mohou mít nejvýše jeden úspěch. Pozdní zápis nesmí obnovit zrušenou rezervaci.
- **BR-07 — Oznámení:** po úspěšném potvrzení nebo zrušení, včetně zrušení návrhu, systém předá Notification Service typ změny, ID rezervace a jejího uživatele. Odmítnutý pokus, Create ani Check Availability oznámení úspěšného přechodu nevyvolává. Předání není zárukou doručení; selhání, pořadí a opakování řeší TBD-06.

**Zdroj pravidel:** intervaly a souběh vycházejí z reference C02; výhradní alokace, kapacita, identita a notifikační hranice z C01. Návrhy politik nejsou schválené jen proto, že odpovídají současnému kódu.

## OP-01 — Vytvořit návrh rezervace (Create Reservation)

**Cíl / hodnota pro uživatele:** zaznamenat záměr využít učebnu bez její alokace.

**Spouštěcí událost:** uživatel odešle učebnu, uživatele rezervace, začátek, konec a počet účastníků.

**Pozorovatelné požadavky:**

- **REQ-01:** při splnění předpokladů systém jedním úspěšným provedením vytvoří právě jeden `DRAFT` se zadanými údaji a jedinečným ID a vrátí ID a stav. Automatické rozpoznání opakovaně doručeného požadavku není tímto požadavkem garantováno.
- **REQ-02:** kolize s jinou rezervací vytvoření návrhu nebrání. Neplatný vstup, neexistující učebna nebo nedostatečné oprávnění vede k odmítnutí bez vytvoření záznamu.

**Předpoklady:** existující učebna, platný interval, kladný celý počet účastníků a identita/oprávnění podle BR-05.

**Stav po úspěšném provedení:** nový `DRAFT` je uložen; vytvoření samo nemění dostupnost učebny.

**Změna stavu:** `[neexistuje] → DRAFT`.

**Odkaz na doménová pravidla / invarianty:** BR-01 až BR-06.

**Hlavní úspěšný scénář:**

1. Uživatel odešle údaje.
2. Systém ověří oprávnění, učebnu a vstupy.
3. Uloží `DRAFT` a vrátí jeho ID a stav.

**Alternativní / chybové výsledky:** neznámá učebna, neúplný interval, `start >= end`, prázdné ID uživatele, nekladný/necelý počet nebo nedostatečné oprávnění → odmítnutí podle BR-06. Kolize ani nadkapacita podle navrženého BR-03 nebrání vzniku návrhu.

**Příklady ověření:**

| Vstup / situace | Očekávaný výsledek |
|---|---|
| U1, kapacita 30, oprávněný uživatel, 20 účastníků, `[10:00,11:00)` | Jeden `DRAFT`, vrácené ID, bez nové alokace |
| `start = end`, chybějící konec nebo neznámá učebna (samostatné případy) | Odmítnutí, žádný nový záznam |
| Prázdné ID; počet 0, −1 nebo 1,5 (samostatné případy) | Odmítnutí, žádný nový záznam |
| Stejný interval již blokuje jiná rezervace | Nový `DRAFT` vznikne |
| 31 účastníků při kapacitě 30 | Podle navrženého BR-03 vznikne `DRAFT`; nelze jej potvrdit |

**Zdůvodnění / zdroj:** C01 a reference Create; vytvoření zaznamenává záměr, potvrzení teprve alokuje učebnu.

**Předpoklad / neznámá / TBD:** TBD-01 až TBD-04.

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

U každého případu ověřit také absenci změn způsobených dotazem.

**Zdůvodnění / zdroj:** reference Availability a výhradní alokace z C01; stejný význam překryvu jako při potvrzení.

**Předpoklad / neznámá / TBD:** TBD-01 a TBD-04.

## OP-03 — Potvrdit rezervaci (Confirm Reservation)

**Cíl / hodnota pro uživatele:** získat platnou alokaci učebny.

**Spouštěcí událost:** oprávněný uživatel požádá o potvrzení rezervace podle ID.

**Pozorovatelné požadavky:**

- **REQ-05:** při splnění předpokladů, kapacity a absence konfliktu systém uloží `CONFIRMED` a vrátí ID a stav dosažený touto operací. Jinak potvrzení odmítne; při souběhu platí BR-06.
- **REQ-06:** při souběžném potvrzování dvou vzájemně se překrývajících návrhů stejné učebny, bez mezilehlého zrušení jejich alokace, uspěje nejvýše jeden. Nekolidující návrhy mohou uspět současně; BR-02 platí vždy.
- **REQ-07:** potvrzení odmítnuté kvůli vstupu, oprávnění, stavu nebo doménovému pravidlu samo nezmění rezervaci ani nevytvoří alokaci; vrátí důvod odmítnutí. Selhání oznámení po uložení je samostatný případ TBD-06.
- **REQ-08:** úspěšný přechod vyvolá oznámení podle BR-07.

**Předpoklady:** rezervace a její učebna existují, stav je `DRAFT`, uživatel má oprávnění. Předchozí kontrola dostupnosti není nutná ani dostačující.

**Stav po úspěšném provedení:** rezervace je `CONFIRMED`, blokuje učebnu, splňuje kapacitu a invariant překryvů; ostatní údaje zůstávají stejné. Za dostupné notifikační služby je předáno oznámení.

**Změna stavu:** `DRAFT → CONFIRMED`.

**Odkaz na doménová pravidla / invarianty:** BR-01 až BR-07.

**Hlavní úspěšný scénář:**

1. Uživatel odešle ID rezervace.
2. Systém ověří oprávnění, existenci rezervace/učebny a stav.
3. Ověří kapacitu a konflikt a uloží potvrzení při zachování BR-02/06.
4. Předá oznámení a vrátí ID a výsledek tohoto potvrzení.

**Alternativní / chybové výsledky:** neznámá rezervace/učebna, nedostatečné oprávnění, jiný stav než `DRAFT`, nadkapacita nebo konflikt → odmítnutí podle BR-06. Bez jiné souběžné změny návrh při konfliktu či nadkapacitě zůstává `DRAFT`. Výsledek chyby oznámení řeší TBD-06.

**Příklady ověření:**

| Situace | Očekávaný výsledek |
|---|---|
| `DRAFT`, 30 účastníků, kapacita 30, bez konfliktu | `CONFIRMED`, blokovaná dostupnost, předané oznámení |
| Stejný návrh s 31 účastníky | Odmítnutí, zůstává `DRAFT`, bez oznámení potvrzení |
| Existuje potvrzená U1 `[10:00,11:00)`, návrh `[10:30,11:30)` | Odmítnutí konfliktu, zůstává `DRAFT` |
| Stejná situace, návrh `[11:00,12:00)` | Potvrzení uspěje |
| Dva jinak platné konfliktní návrhy se potvrzují současně, bez rušení či technické chyby | Jeden uspěje, druhý je odmítnut; nevznikne dvojí alokace |
| Dva nekolidující návrhy se potvrzují současně | Oba při splnění ostatních podmínek uspějí |
| Stav již `CONFIRMED` nebo `CANCELLED` | Odmítnutí, stav nezměněn |
| Po dotazu na volný interval mezitím uspělo jiné konfliktní potvrzení | Odmítnutí konfliktu |

**Zdůvodnění / zdroj:** reference Confirm, zákaz dvojí alokace a kapacitní pravidlo učeben z C01.

**Předpoklad / neznámá / TBD:** TBD-01 až TBD-06.

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
3. Uloží `CANCELLED` při zachování BR-06, předá oznámení a vrátí ID a výsledek zrušení.

**Alternativní / chybové výsledky:** neznámá rezervace, nedostatečné oprávnění nebo již `CANCELLED` → odmítnutí bez nové změny a oznámení úspěchu. Souběh řeší BR-06, chybu oznámení TBD-06.

**Příklady ověření:**

| Situace | Očekávaný výsledek |
|---|---|
| Existující `DRAFT` | `CANCELLED`, zachované údaje, předané oznámení |
| `CONFIRMED`, žádná jiná blokující rezervace v intervalu | `CANCELLED`, interval je dostupný, záznam zachován |
| Opakované zrušení | Odmítnutí, stále `CANCELLED`, bez nového oznámení |
| Neznámé ID | Odmítnutí, žádná změna |
| Zrušení před začátkem, přesně na začátku nebo po konci | Podle navrženého BR-04 uspěje |
| Souběžné Confirm a Cancel původního `DRAFT`, ostatní podmínky splněny | Konečný `CANCELLED`, odpovědi odpovídají pořadí z BR-06 |

**Zdůvodnění / zdroj:** reference Cancel a životní cyklus z C01. Časová hranice z referenčního příkladu není povinná politika; naše odchylka vyžaduje přijetí TBD-03.

**Předpoklad / neznámá / TBD:** TBD-03 až TBD-06.

## Otevřená rozhodnutí před schválením

| ID | Co musí tým uzavřít |
|---|---|
| TBD-01 | Časové pásmo a interpretaci času včetně změn letního času. Příklady předpokládají společnou interpretaci a běžný den bez změny času. |
| TBD-02 | Přijmout nadkapacitní `DRAFT`, nebo kontrolovat horní mez již při Create. První varianta vyžaduje změnit obecné pravidlo C01 na pravidlo pro potvrzené rezervace. |
| TBD-03 | Přijmout absenci časových omezení, nebo určit přípustnost operací v minulosti a hranici rušení včetně zdroje aktuálního času. Dvouhodinová hranice C01 zůstává budoucí změnou. |
| TBD-04 | Kdo smí vytvářet rezervace za jiné, potvrzovat, rušit a zjišťovat dostupnost. Poté doplnit konkrétní příklady povoleného a zakázaného přístupu. |
| TBD-05 | Přijmout výsledky souběhu podle BR-06. Ověřit obě pořadí Confirm/Cancel i skutečně souběžné pokusy. |
| TBD-06 | Určit výsledek při selhání Notification Service po uložení změny, potřebu opakování a pořadí oznámení. Potvrdit oznámení i pro zrušení `DRAFT`. Úspěšné příklady předpokládají dostupnou službu. |

Kontrola požadavků před úpravou je zachycena v [review](specification-v0.1-review.md). Po rozhodnutí týmu se sjednotí dotčené dokumenty C01 a doplní diagramy. Jejich shoda ani skutečné provedení příkladů zatím nejsou ověřeny. Dokument sám neprokazuje týmové schválení ani shodu aplikace.
