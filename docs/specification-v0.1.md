# Specifikace základního chování — v0.1

**Projekt:** Rezervační systém učeben  
**Stav:** Návrh k týmové revizi, dosud neschváleno.

Dokument popisuje požadované pozorovatelné chování. Není potvrzením, že současná implementace všechny požadavky splňuje. Zahrnuje vytvoření návrhu, ověření dostupnosti, potvrzení a zrušení rezervace. Schvalování oprávněnou osobou (`Approve`) není součástí v0.1.

## Pojmy

- **Učebna (Resource):** existující prostor s identifikátorem, označením a maximální kapacitou. Rezervuje se jako celek; volná místa neopravňují k souběžné rezervaci stejné učebny.
- **Uživatel:** student nebo vyučující identifikovaný ověřeným uživatelským ID.
- **Rezervace (Reservation):** záznam s ID, ID učebny, ID uživatele, začátkem, koncem, počtem účastníků a stavem.
- **DRAFT:** návrh, který dosud nealokuje učebnu.
- **CONFIRMED:** potvrzená rezervace, která alokuje učebnu pro svůj interval.
- **CANCELLED:** zrušená rezervace, která učebnu nealokuje; záznam zůstává zachován.

## Doménová pravidla a invarianty

### BR-01 — Časové intervaly

Začátek a konec musí být vyplněny a musí platit `start < end`. Interval má význam `[start, end)`: začátek zahrnuje, konec nezahrnuje. Intervaly A a B se překrývají právě tehdy, když `A.start < B.end` a současně `B.start < A.end`. Navazující intervaly se nepřekrývají.

**Zdroj / důvod:** existující kontrola překryvů a potřeba umožnit bezprostředně navazující využití učebny.

**TBD-01:** tým musí určit interpretaci zadávaných časů a časové pásmo, včetně nejednoznačných časů při změně letního času. Současný model používá `LocalDateTime`. Příklady níže předpokládají stejný běžný den bez změny času a společnou časovou interpretaci.

### BR-02 — Výhradní alokace a dostupnost

Učebnu blokují pouze rezervace ve stavu `CONFIRMED`. V žádném uloženém výsledném stavu nesmějí existovat dvě překrývající se potvrzené rezervace stejné učebny. Toto pravidlo platí i při souběžných operacích. Rezervace různých učeben se navzájem neblokují.

Výsledek ověření dostupnosti popisuje stav při vyhodnocení dotazu. Nevytváří rezervaci ani příslib budoucího potvrzení; při potvrzení se konflikt vyhodnocuje znovu.

**Zdroj / důvod:** společné business pravidlo z C01; zabránění dvojí rezervaci učebny.

### BR-03 — Kapacita a počet účastníků

Počet účastníků musí být kladné celé číslo. Potvrdit lze pouze rezervaci, jejíž počet účastníků nepřekračuje kapacitu učebny. Rovnost s kapacitou je přípustná.

**Návrh k přijetí (TBD-02):** návrh `DRAFT` smí kapacitu překračovat; kontrola horní meze proběhne při potvrzení. Tato politika odpovídá současnému kódu, ale zpřesňuje obecnější formulaci pravidla v C01. Tým musí přijmout toto zpřesnění, nebo požadovat odmítnutí již při vytvoření. Následující operace a příklady používají tuto navrženou politiku.

**Zdroj / důvod:** vlastní business pravidlo z C01 a současná implementace kontroly kapacity při potvrzení.

### BR-04 — Životní cyklus a rušení

Vytvoření vede do `DRAFT`. Potvrzení je přípustné pouze z `DRAFT` a vede do `CONFIRMED`. Zrušení je přípustné z `DRAFT` i `CONFIRMED` a vede do `CANCELLED`. Z `CANCELLED` není povolen další přechod. Opakované potvrzení i opakované zrušení se odmítne.

Zrušení uchovává záznam a jeho ID, učebnu, uživatele, interval i počet účastníků. Mění pouze stav a ruší případnou alokaci.

**Návrh k přijetí (TBD-03):** baseline nemá časový limit pro zrušení ani zákaz vytvoření či potvrzení intervalu v minulosti. Zrušit lze i rezervaci, která již začala nebo skončila. To odpovídá současnému modelu. Dvouhodinová hranice z C01 je budoucí změna, nikoli přijaté pravidlo v0.1. Tým musí tuto politiku výslovně přijmout, nebo definovat jinou včetně hranice a zdroje aktuálního času.

**Zdroj / důvod:** stávající životní cyklus a oddělení základního chování od budoucí změny.

### BR-05 — Identita a oprávnění

Vytvořená rezervace musí obsahovat neprázdné ID uživatele. Podle předpokladu C01 ověření identity zajišťuje externí systém; samotné vyplnění ID není důkazem autentizace. Operace vyžadující oprávnění při jeho nesplnění nesmějí změnit data ani zpřístupnit chráněný výsledek.

**TBD-04:** určit, zda potvrzení a zrušení smí provést pouze vlastník, případně také správce, zda lze vytvářet rezervaci za jiného uživatele a zda dostupnost smí zjišťovat nepřihlášený uživatel. Do rozhodnutí nejsou autorizační scénáře úplně specifikovány. Student a vyučující mají podle C01 stejné rezervační možnosti.

**Zdroj / důvod:** předpoklad externí identity z Project Frame a potřeba jednoznačně určit oprávněné aktéry.

### BR-06 — Odmítnutí operace a souběh

Při odmítnutí pro neplatný vstup, nepovolený stav, nedostatečné oprávnění nebo porušení doménového pravidla operace nezapíše částečný výsledek. Vrátí rozpoznatelný důvod odmítnutí. Stav se může mezitím změnit jinou úspěšnou souběžnou operací.

**Navržená politika souběhu (TBD-05):** změny jedné rezervace mají výsledek odpovídající některému postupnému pořadí operací. Při souběhu potvrzení a zrušení původního `DRAFT`, jsou-li ostatní podmínky splněny:

- potvrzení proběhne první → může uspět potvrzení i následné zrušení; konečný stav je `CANCELLED`;
- zrušení proběhne první → zrušení uspěje a potvrzení se odmítne; konečný stav je `CANCELLED`.

Pozdní potvrzení nesmí přepsat již dokončené zrušení. Při dvou souběžných potvrzeních téže rezervace uspěje nejvýše jedno; obdobně při dvou zrušeních. Pro různé konfliktní rezervace vždy platí BR-02.

**Zdroj / důvod:** požadavek C02 určit pozorovatelný výsledek souběhu; ochrana před ztrátou změny stavu.

### BR-07 — Oznámení

Po úspěšném potvrzení nebo zrušení systém předá požadavek na příslušné oznámení službě Notification Service. Odmítnutá operace nesmí vyvolat oznámení o úspěšném přechodu. Vytvoření návrhu a kontrola dostupnosti oznámení nevyvolávají.

**TBD-06:** určit chování při nedostupnosti Notification Service, zejména výsledek vrácený uživateli a případné opakování doručení. Úspěšné scénáře níže předpokládají dostupnou službu. Pro lokální demonstraci lze použít adaptér zapisující oznámení do logu; tím se neprokazuje skutečné doručení uživateli.

**Zdroj / důvod:** systémová hranice definovaná v C01.

## OP-01 — Vytvořit návrh rezervace (Create Reservation)

**Cíl / hodnota pro uživatele:** zaznamenat záměr využít učebnu, který lze následně potvrdit nebo zrušit.

**Spouštěcí událost:** uživatel odešle ID učebny, ID uživatele rezervace, začátek, konec a počet účastníků.

**Pozorovatelný požadavek / požadavky:**

- **REQ-01:** při splnění předpokladů systém vytvoří právě jeden nový návrh `DRAFT` s jedinečným ID a zadanými údaji a vrátí jeho ID a stav. Návrh nealokuje učebnu.
- **REQ-02:** existující kolize s jinou rezervací nebrání vytvoření návrhu. Neplatný vstup nebo neexistující učebna vede k odmítnutí bez vytvoření záznamu.

**Předpoklady:**

- učebna existuje;
- interval splňuje BR-01;
- počet účastníků je kladné celé číslo podle BR-03;
- identita a oprávnění splňují BR-05.

**Stav po úspěšném provedení:** existuje nový záznam s původními údaji a stavem `DRAFT`; dostupnost učebny se jeho vytvořením nemění.

**Změna stavu:** `[neexistující rezervace] → DRAFT`.

**Odkaz na doménová pravidla / invarianty:** BR-01, BR-02, BR-03, BR-04, BR-05, BR-06.

**Hlavní úspěšný scénář:**

1. Uživatel odešle údaje návrhu.
2. Systém ověří oprávnění, existenci učebny a platnost vstupů.
3. Systém vytvoří a uloží návrh ve stavu `DRAFT`.
4. Systém vrátí jeho ID a stav.

**Alternativní / chybové výsledky:**

- neexistující učebna → odmítnutí;
- nevyplněný interval nebo `start >= end` → odmítnutí;
- chybějící či prázdné ID uživatele → odmítnutí;
- nekladný nebo neceločíselný počet účastníků → odmítnutí;
- nedostatečné oprávnění → odmítnutí podle BR-05;
- kolize nebo překročení kapacity → podle navržených BR-02 a BR-03 vznikne `DRAFT`, nikoli alokace.

**Příklady ověření:**

| Vstup / výchozí situace | Očekávaný výsledek |
|---|---|
| Učebna U1, kapacita 30, ověřený oprávněný uživatel, 20 účastníků, `[10:00, 11:00)` | Jeden nový `DRAFT`, vrácené ID, dostupnost nezměněna |
| `start = end = 10:00` | Odmítnutí, žádný nový záznam |
| Neznámé ID učebny | Odmítnutí, žádný nový záznam |
| Počet účastníků 0 | Odmítnutí, žádný nový záznam |
| U1 již má `CONFIRMED` v `[10:00, 11:00)` | Návrh stejného intervalu vznikne jako `DRAFT` |
| U1 má kapacitu 30, požadováno 31 účastníků | Podle navrženého BR-03 vznikne `DRAFT`; potvrzení nebude přípustné |

**Zdůvodnění / zdroj:** C01 odděluje vytvoření návrhu od potvrzení. Uživatel může zaznamenat záměr, aniž by blokoval učebnu ostatním.

**Předpoklad / neznámá / TBD:** TBD-01, TBD-02, TBD-03 a TBD-04 ze společných pravidel.

## OP-02 — Ověřit dostupnost učebny (Check Availability)

**Cíl / hodnota pro uživatele:** zjistit, zda je učebna pro zvolený interval časově volná.

**Spouštěcí událost:** uživatel odešle ID učebny a požadovaný interval.

**Pozorovatelný požadavek / požadavky:**

- **REQ-03:** pro existující učebnu a platný interval systém vrátí `UNAVAILABLE`, pokud existuje překrývající se blokující rezervace podle BR-02; jinak vrátí `AVAILABLE`.
- **REQ-04:** kontrola dostupnosti nemění žádnou rezervaci ani nevytváří alokaci. Neexistující učebnu nebo neplatný interval odmítne místo vrácení výsledku dostupnosti.

**Předpoklady:** učebna existuje, interval splňuje BR-01 a přístup splňuje BR-05.

**Stav po úspěšném provedení:** uživatel obdrží výsledek dostupnosti; uložená data se touto operací nezmění. `UNAVAILABLE` je platný výsledek dotazu, nikoli chyba operace.

**Změna stavu:** žádná.

**Odkaz na doménová pravidla / invarianty:** BR-01, BR-02, BR-05, BR-06.

**Hlavní úspěšný scénář:**

1. Uživatel zadá učebnu a interval.
2. Systém ověří přístup, existenci učebny a platnost intervalu.
3. Systém vyhodnotí překryv s blokujícími rezervacemi.
4. Systém vrátí `AVAILABLE` nebo `UNAVAILABLE`.

**Alternativní / chybové výsledky:**

- neexistující učebna → odmítnutí;
- neplatný nebo neúplný interval → odmítnutí;
- nedostatečné oprávnění, pokud je přístup omezen → odmítnutí podle BR-05;
- souběžná změna rezervací → výsledek není zárukou úspěchu budoucího potvrzení podle BR-02.

**Příklady ověření:**

Pro první tři příklady existuje pouze jedna potvrzená rezervace U1 v `[10:00, 11:00)`.

| Dotaz / výchozí situace | Očekávaný výsledek |
|---|---|
| U1, `[09:00, 10:00)` | `AVAILABLE` |
| U1, `[10:30, 11:30)` | `UNAVAILABLE` |
| U1, `[11:00, 12:00)` | `AVAILABLE` |
| Překrývají se pouze `DRAFT` a `CANCELLED` | `AVAILABLE` |
| Stejný interval je potvrzen pouze pro jinou učebnu | `AVAILABLE` |
| Neexistující učebna nebo `[11:00, 10:00)` | Odmítnutí, nikoli `AVAILABLE` |

Ve všech příkladech se ověří také to, že dotaz nezměnil uložené rezervace.

**Zdůvodnění / zdroj:** základní operace z C01 a invariant výhradní alokace. Dotaz zjišťuje časovou dostupnost; bez počtu účastníků neposuzuje kapacitní vhodnost učebny.

**Předpoklad / neznámá / TBD:** TBD-01 a TBD-04 ze společných pravidel.

## OP-03 — Potvrdit rezervaci (Confirm Reservation)

**Cíl / hodnota pro uživatele:** změnit návrh na platnou alokaci učebny.

**Spouštěcí událost:** oprávněný uživatel požádá o potvrzení rezervace podle jejího ID.

**Pozorovatelný požadavek / požadavky:**

- **REQ-05:** systém potvrdí existující `DRAFT` pouze tehdy, pokud splňuje kapacitní pravidlo a nekoliduje s potvrzenou rezervací stejné učebny. Vrátí ID a stav `CONFIRMED`.
- **REQ-06:** ze souběžných pokusů potvrdit různé konfliktní návrhy téže učebny může uspět nejvýše jeden; invariant BR-02 zůstane zachován.
- **REQ-07:** neúspěšné potvrzení samo nezmění stav rezervace ani nevytvoří alokaci; vrátí důvod odmítnutí podle BR-06.
- **REQ-08:** úspěšné potvrzení vyvolá požadavek na oznámení podle BR-07.

**Předpoklady:** rezervace i její učebna existují, rezervace je `DRAFT` a uživatel má oprávnění podle BR-05. Podmínkami úspěšného přechodu jsou BR-02 a BR-03; dřívější dotaz na dostupnost není nutný ani dostačující.

**Stav po úspěšném provedení:** rezervace je `CONFIRMED`, blokuje učebnu pro svůj interval a nepřekračuje kapacitu. Ostatní údaje rezervace zůstávají zachovány; požadavek na oznámení byl předán.

**Změna stavu:** `DRAFT → CONFIRMED`.

**Odkaz na doménová pravidla / invarianty:** BR-01 až BR-07.

**Hlavní úspěšný scénář:**

1. Uživatel odešle ID rezervace k potvrzení.
2. Systém ověří oprávnění, existenci rezervace a její učebny a výchozí stav.
3. Systém ověří kapacitu a absenci konfliktu pro interval rezervace.
4. Systém uloží přechod do `CONFIRMED` při zachování pravidel souběhu.
5. Systém předá požadavek na oznámení a vrátí ID a aktuální stav.

**Alternativní / chybové výsledky:**

- neexistující rezervace nebo její učebna → odmítnutí;
- stav `CONFIRMED` nebo `CANCELLED` → odmítnutí nepovoleného přechodu;
- překročená kapacita → odmítnutí; bez jiné souběžné operace zůstává `DRAFT`;
- překryv s potvrzenou rezervací stejné učebny → odmítnutí; bez jiné souběžné operace zůstává `DRAFT`;
- nedostatečné oprávnění → odmítnutí;
- souběžné potvrzení či zrušení → výsledek podle BR-02 a BR-06;
- selhání notifikační služby → dosud otevřený bod TBD-06.

**Příklady ověření:**

| Výchozí situace | Očekávaný výsledek |
|---|---|
| U1 má kapacitu 30, `DRAFT` pro 30 účastníků, bez konfliktu | `CONFIRMED`, interval je nedostupný, vznikne požadavek na oznámení |
| Stejná situace s 31 účastníky | Odmítnutí, zůstává `DRAFT`, bez oznámení o potvrzení |
| Existuje `CONFIRMED` U1 `[10:00, 11:00)`, návrh je `[10:30, 11:30)` | Odmítnutí konfliktu, zůstává `DRAFT` |
| Existuje `CONFIRMED` U1 `[10:00, 11:00)`, návrh je `[11:00, 12:00)` | Potvrzení uspěje |
| Dva jinak platné konfliktní návrhy U1 se potvrzují současně | Nejvýše jeden přejde do `CONFIRMED`, nevznikne dvojí alokace |
| Dostupnost byla volná, ale mezitím uspělo jiné konfliktní potvrzení | Potvrzení se odmítne |
| Rezervace je již `CANCELLED` | Odmítnutí, zůstává `CANCELLED` |

**Zdůvodnění / zdroj:** okamžik alokace podle C01; společné pravidlo zákazu překryvů a vlastní pravidlo kapacity. Samostatné schvalování není součástí tohoto přechodu. Model učebny v C01 neobsahuje příznak aktivní/neaktivní, proto jej tato specifikace nezavádí jen na základě obecného příkladu zadání.

**Předpoklad / neznámá / TBD:** TBD-01 až TBD-06 podle dotčených společných pravidel.

## OP-04 — Zrušit rezervaci (Cancel Reservation)

**Cíl / hodnota pro uživatele:** odvolat návrh nebo uvolnit dříve rezervovanou učebnu při zachování záznamu rezervace.

**Spouštěcí událost:** oprávněný uživatel požádá o zrušení rezervace podle jejího ID.

**Pozorovatelný požadavek / požadavky:**

- **REQ-09:** systém umožní zrušit existující rezervaci podle BR-04 a vrátí její ID a stav `CANCELLED`.
- **REQ-10:** po úspěšném zrušení rezervace neblokuje dostupnost učebny a její záznam zůstává zachován.
- **REQ-11:** nepřípustné nebo neoprávněné zrušení se odmítne podle BR-05 a BR-06. Souběh s potvrzením nesmí obnovit již zrušenou rezervaci.
- **REQ-12:** úspěšné zrušení vyvolá požadavek na oznámení podle BR-07.

**Předpoklady:** rezervace existuje, má stav `DRAFT` nebo `CONFIRMED` a uživatel má oprávnění podle BR-05. Přípustnost v čase se řídí navrženým BR-04.

**Stav po úspěšném provedení:** rezervace je `CANCELLED`, neblokuje učebnu a je nadále evidována se stejnými údaji. Požadavek na oznámení byl předán. Dostupnost dotazovaného intervalu mohou nadále omezovat jiné potvrzené rezervace.

**Změna stavu:** `DRAFT → CANCELLED` nebo `CONFIRMED → CANCELLED`.

**Odkaz na doménová pravidla / invarianty:** BR-02, BR-04, BR-05, BR-06, BR-07.

**Hlavní úspěšný scénář:**

1. Uživatel odešle ID rezervace ke zrušení.
2. Systém ověří oprávnění, existenci rezervace a přípustnost zrušení.
3. Systém uloží přechod do `CANCELLED` při zachování záznamu a pravidel souběhu.
4. Systém předá požadavek na oznámení a vrátí ID a aktuální stav.

**Alternativní / chybové výsledky:**

- neexistující rezervace → odmítnutí;
- již `CANCELLED` → odmítnutí, záznam zůstává nezměněn a nevznikne nové oznámení o zrušení;
- nedostatečné oprávnění → odmítnutí;
- souběh s potvrzením nebo dalším zrušením → výsledek podle BR-06;
- selhání notifikační služby → dosud otevřený bod TBD-06.

**Příklady ověření:**

| Výchozí situace | Očekávaný výsledek |
|---|---|
| Existující `DRAFT` | `CANCELLED`, záznam zachován, požadavek na oznámení |
| `CONFIRMED` U1 `[10:00, 11:00)`, žádná jiná potvrzená rezervace v intervalu | `CANCELLED`, dotaz na stejný interval vrátí `AVAILABLE` |
| Opakované zrušení stejné rezervace | Odmítnutí, stále `CANCELLED`, bez dalšího oznámení |
| Neznámé ID | Odmítnutí, žádný záznam nevznikne ani se nezmění |
| Zrušení přesně na začátku nebo po konci rezervace | Podle navrženého BR-04 uspěje |
| Souběžné potvrzení a zrušení původního `DRAFT`, ostatní podmínky splněny | Konečný stav `CANCELLED`; jednotlivé výsledky odpovídají jednomu z pořadí v BR-06 |

**Zdůvodnění / zdroj:** životní cyklus z C01, potřeba uvolnit alokaci a zachovat informaci o zrušené rezervaci. Zrušení neznamená fyzické smazání.

**Předpoklad / neznámá / TBD:** TBD-03 až TBD-06 ze společných pravidel.

## Podmínka přijetí návrhu

Tým musí před označením „Specification Baseline v0.1 — schválená týmem“ vyřešit TBD-01 až TBD-06, promítnout rozhodnutí do operací a ověřovacích příkladů a provést kontrolu přijetí požadavků podle zadání C02. Příklady v tomto dokumentu jsou návrhy ověření, nikoli záznam skutečně provedených testů. Diagramy a evidence spuštění budou doplněny v dalších krocích.
