# Project Frame

## Reservation domain
Učebny (vzdělávací, přednáškové nebo zasedací prostory).

## Purpose
Systém slouží studentům a vyučujícím k efektivní rezervaci sdílených školních prostor. Cílem je zabránit konfliktům v čase ("double-booking") a zajistit, že vybraná učebna kapacitně odpovídá plánované události.

## Users / Stakeholders
* Student
* Vyučující
* (Správce budovy)

## Core concepts
* **Reservation** (Rezervace)
* **Resource** (Učebna)
* **User** (Uživatel - žadatel)

## Core operations
* Create reservation
* Confirm / approve reservation
* Cancel reservation
* Check availability

## Persistent state
* **Resource:** ID, označení (název), maximální kapacita.
* **Reservation:** ID, identifikátor učebny (Resource ID), identifikátor uživatele (User ID), časový interval (začátek a konec), počet účastníků, aktuální stav.

## State-changing operation
* **Potvrzení:** `DRAFT` → `CONFIRMED`
* **Zrušení:** `DRAFT` nebo `CONFIRMED` → `CANCELLED`

## Common business rule
Confirmed reservations for the same resource must not overlap.

## Domain-specific business rule
Počet účastníků rezervace nesmí překročit maximální kapacitu dané učebny.

## External / system boundary
Notification Service (zajišťuje asynchronní odesílání upozornění uživatelům při potvrzení nebo zrušení rezervace).

## Assumption
Předpokládáme, že autentizaci uživatelů kompletně řeší externí systém (Identity Provider) a naše aplikace pracuje pouze s ověřenými uživatelskými ID.

## Unknown
Zatím není jisté, zda a jak se budou do systému synchronizovat pevné rozvrhy výuky z univerzitního informačního systému (zda půjdou naimportovat jako série klasických rezervací, nebo budou mít speciální datový model).

---

# Účel systému a očekávané změny (Intent and Change)

## 1. Účel systému (Business Intent)
Hlavním účelem aplikace je poskytnout spolehlivý, transparentní a bezkonfliktní nástroj pro rezervaci sdílených prostor (učeben). 

**Klíčové cíle (Core Drivers):**
*   **Zabránění konfliktům:** Eliminace "double-bookingu". Systém garantuje, že v jeden čas může mít učebnu potvrzenou pouze jeden subjekt.
*   **Efektivní využití kapacit:** Systém vynucuje, aby počet účastníků nepřekročil fyzickou kapacitu učebny, čímž předchází bezpečnostním a logistickým problémům.
*   **Transparentní životní cyklus:** Rezervace prochází jasně definovanými stavy, což odděluje záměr (návrh) od závazného potvrzení.

## 2. Hranice kontextu (Bounded Context)
Pro zachování jednoduchosti a zaměření systému jsou definovány striktní hranice toho, co systém **neřeší**:
*   **Identity a Access Management (IAM):** Systém neřeší registraci uživatelů ani přihlašování.
*   **Fyzické doručování zpráv:** Aplikace určuje obsah notifikace a spouštěč, ale doručení deleguje na externí `Notification Service`.

## 3. Očekávané změny v čase (Anticipated Changes)
Během životního cyklu aplikace očekáváme rozšiřování požadavků v následujících oblastech:
*   **Opakující se rezervace:** Požadavek na vytvoření série rezervací (např. "každé úterý v 10:00 do konce semestru").
*   **Role a priority:** Zavedení byznysové logiky, kdy uživatel s rolí "Vyučující" může převzít potvrzenou rezervaci "Studenta".
*   **Nové notifikační kanály:** Přechod z E-mailů např. na MS Teams, Slack nebo mobilní push notifikace.