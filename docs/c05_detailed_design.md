## C05 Podrobný návrh

**Vybraná architektonická jednotka:**
Workflow schvalování a expirace rezervací (správa rezervací), včetně koordinace transakcí a expirace v `BeanConfiguration` a spouštěče `ApprovalExpiry`.

**Související požadavky:**
REQ-06–08, REQ-14, REQ-16.

**Související scénáře:**
OP-05 — Schválení rezervace, OP-08 — Vypršení čekajícího schválení.

**Relevantní obchodní pravidla:**
BR-01 (Interval), BR-02 (Přidělování), BR-03 (Kapacita), BR-04 (Životní cyklus), BR-05 (Oprávnění), BR-06 (Zamítnutí/souběh), BR-07 (Oznámení), BR-08 (Zdroje vyžadující schválení), BR-09 (Platnost požadavku).

### Podrobné odpovědnosti

| Podrobná odpovědnost | Proč je potřeba? |
| ----- | ----- |
| Ověření oprávnění schvalovatele | Rozhodovat mohou pouze administrátoři uvedení v konfiguraci serveru (`reservation.approvers`); klient nikdy neposílá důvěryhodnou roli (BR-05). |
| Provedení expirace před operací | Požadavek, jehož začátek již nastal, musí být před vyhodnocením jakéhokoli rozhodnutí ve stavu `EXPIRED`. Hromadná aktualizace se potvrzuje ve vlastní transakci, aby pozdější zamítnuté rozhodnutí nemohlo expiraci vrátit zpět (BR-09, BR-06). |
| Získání pesimistického zámku řádku | Serializuje souběžné operace Schválit, Zamítnout a Zrušit nad stejnou rezervací; čekající operace po získání zámku vyhodnotí aktuální stav a čas (BR-06). |
| Vyhodnocení životního cyklu a doménových invariantů | Rezervace musí být stále ve stavu `PENDING_APPROVAL`, musí platit `now < start` a `participantCount <= capacity` (BR-03, BR-04, BR-09). |
| Zjištění konfliktních přidělení | Žádná jiná rezervace ve stavu `CONFIRMED` nesmí zabírat stejný zdroj ve stejném časovém intervalu; při konfliktu zůstane požadavek čekající (BR-02, BR-08). |
| Provedení změny stavu | Atomicky změní stav rezervace na `CONFIRMED` a uloží ji do PostgreSQL; omezení `EXCLUDE USING gist` je poslední ochranou proti závodním podmínkám mezi různými rezervacemi. |
| Odeslání oznámení | `NotificationPort` se vyvolá až po potvrzení databázové transakce, takže selhání oznámení nikdy nemůže vrátit schválení zpět (BR-07). |
| Pravidelná materializace expirace | Čekající požadavky vyprší bez zásahu uživatele a tento mechanismus funguje i po restartu aplikace, protože stav a čas začátku jsou uloženy v PostgreSQL (BR-09, OP-08). |

### Návrhové prvky a jejich odpovědnost

| Odpovědnost | Vlastník | Stav/data | Proč právě zde? |
| ----- | ----- | ----- | ----- |
| Oprávnění schvalovatelů a koordinace rozhodnutí | `ReservationService` | Množina ID schvalovatelů, porty | Koordinuje rozhodnutí a volá doménovou logiku; doména není závislá na frameworku (ADR-001). |
| Hranice transakcí, spouštěč expirace před operací a callback oznámení po potvrzení transakce | `BeanConfiguration` (obal kolem `ReservationService`) | `TransactionTemplate`, `TransactionSynchronization` | Transakce jsou záležitostí Springu, proto jsou vymezeny v infrastruktuře; pravidla ArchUnit zakazují použití Springu v `domain`. |
| Rozhodnutí o expiraci (určení času) | `ReservationService.expirePendingApprovals()` | `Clock` | Obchodní hranicí je začátek rezervace; služba vyhodnocuje čas serveru (UTC) a požádá úložiště o podmíněný přechod. |
| Spouštěč expirace | `ApprovalExpiry` | žádné | Pouze žádá o expiraci prostřednictvím vstupního portu; nezná úložiště, transakce ani hodiny (pravidlo ArchUnit `expiryTriggerUsesInboundPort`). |
| Doménové invarianty | `Reservation` (entita) | `state`, `startTime`, `participantCount` | Entita je zdrojem pravdy pro přechody životního cyklu; jako parametry přijímá `Resource` a čas a nezná žádné úložiště. |
| Omezení zdrojů | `Resource` (entita) | `capacity`, `requiresApproval` | Zdroj obsahuje vlastní fyzický limit a pravidla schvalování. |
| Perzistence, zamykání a podmíněná expirace | `ReservationRepository` (adaptér + `ReservationJpaRepository`) | Zámek řádku, `EXCLUDE USING gist`, `expirePending` | Načítání dat (`findByIdForUpdate`), dotaz na konflikty, ukládání a idempotentní aktualizace `PENDING_APPROVAL → EXPIRED`. |
| Externí zprávy | `NotificationPort` (`LoggingNotificationAdapter`) | Výstup do logu | Odděluje hranici externích oznámení od domény. |

### Klíčová rozhraní / kontrakty

```text
<<interface>> ReservationUseCase (vstupní port)

Reservation approveReservation(Long reservationId, String requestingUserId);
void expirePendingApprovals();

<<interface>> ReservationRepository (výstupní port)

Optional<Reservation> findByIdForUpdate(Long id);
List<Reservation> findConfirmedByResourceAndTimeRange(Long resourceId, LocalDateTime start, LocalDateTime end);
Reservation save(Reservation reservation);
void expirePending(LocalDateTime now);
```

Identifikátory jsou typu `Long` (databázový `BIGSERIAL`), všechny časy jsou `LocalDateTime` v UTC (BR-01). Operace vrací aktualizovanou `Reservation`.

**Kontrakt metody `approveReservation`:**

* **Předpoklady:**

  * `requestingUserId` je zadáno a nachází se v množině schvalovatelů nakonfigurované na serveru.
  * `reservationId` identifikuje existující rezervaci, jejíž zdroj také existuje.

* **Následné podmínky:**

  * Stav rezervace se změní z `PENDING_APPROVAL` na `CONFIRMED`.
  * `participantCount` nepřekročí kapacitu zdroje a platí `now < start`.
  * Žádná jiná rezervace ve stavu `CONFIRMED` se nepřekrývá na stejném zdroji.
  * Odeslání oznámení se vyžádá až po potvrzení transakce.

* **Chybové stavy** (neúspěšné rozhodnutí nic nezmění; již potvrzená expirace zůstane zachována):

  * `401`, pokud chybí hlavička identity; `403`, pokud uživatel není schvalovatelem.
  * `404`, pokud rezervace nebo její zdroj neexistuje.
  * `409 INVALID_STATE`, pokud rezervace není ve stavu `PENDING_APPROVAL` (včetně stavu `EXPIRED`).
  * `409 BUSINESS_RULE_VIOLATION`, pokud je překročena kapacita, interval se překrývá s potvrzenou rezervací (požadavek zůstane čekající) nebo platí `now >= start`.
  * `409` také v případě, že databázové omezení odmítne souběžné konfliktní přidělení.

**Kontrakt metody `expirePendingApprovals`:**

* **Předpoklad:** žádný (systémová operace bez identity uživatele).
* **Následná podmínka:** každá uložená rezervace ve stavu `PENDING_APPROVAL`, pro kterou platí `start <= now`, přejde do stavu `EXPIRED`; všechny ostatní rezervace zůstanou beze změny. Opakované spuštění již nic dalšího nezmění.
* **Vedlejší účinky:** žádná změna přidělení, žádné oznámení.

### Stručný strukturální pohled

```text
[ReservationController] ──► <<interface>> [ReservationUseCase] ◄── [ApprovalExpiry]
                                         ▲
                                          │ implementuje
                   [ReservationService]  (obaleno pomocí [BeanConfiguration]:
                         │ │ │             transakce, expirace před operací, callback po potvrzení)
                         │ │ └──────────► <<interface>> [NotificationPort]
                         │ └────────────► <<interface>> [ResourceRepository]
                         ├──────────────► <<interface>> [ReservationRepository]
                         └──────────────► [Reservation] ···(parametr)···► [Resource]
```

`Reservation` není závislá na žádném úložišti; služba načte `Resource` a předá jej metodě `Reservation.approve`.

### Podrobný průběh

Schválení (OP-05):

1. `ReservationController` přijme požadavek a ID volajícího; chybějící identita vrátí `401` (`RequestIdentity`).
2. Controller zavolá `ReservationUseCase.approveReservation`; bean je obalem `ReservationService` vytvořeným pomocí `BeanConfiguration`.
3. Obal spustí `expirePendingApprovals()` ve vlastní transakci (T1): služba požádá `ReservationRepository.expirePending(now)`; T1 se potvrdí.
4. Obal otevře transakci rozhodnutí (T2) a zavolá `ReservationService.approveReservation`.
5. `ReservationService.requireApprover` zkontroluje množinu schvalovatelů (jinak `403`).
6. `ReservationRepository.findByIdForUpdate` načte rezervaci a drží zámek řádku až do konce T2 (`404`, pokud neexistuje).
7. `ResourceRepository.findById` načte související zdroj (`404`, pokud neexistuje).
8. Služba ověří, že stav je `PENDING_APPROVAL` (jinak `409 INVALID_STATE`).
9. `ReservationService.hasOverlapWithConfirmedReservations` vyhledá konfliktní přidělení ve stavu `CONFIRMED`; při konfliktu operaci ukončí s `409` a požadavek ponechá ve stavu čekání.
10. `Reservation.approve(resource, now)` ověří `now < start` a kapacitu a změní stav na `CONFIRMED`.
11. `ReservationRepository.save` uloží změnu; omezení `no_overlapping_confirmed_reservations` je poslední pojistkou.
12. Služba zavolá `NotificationPort.notifyConfirmed`; port obalu pouze zaregistruje callback po potvrzení transakce.
13. T2 se potvrdí a uvolní zámek. Porušení databázového omezení se projeví zde a změní se na `409`; v takovém případě se oznámení neodešle.
14. Po potvrzení transakce callback zavolá `LoggingNotificationAdapter.notifyConfirmed`; případná chyba se zaznamená do logu a ignoruje.

Expirace (OP-08):

A. `ApprovalExpiry.expire()` se spouští každou sekundu a volá `ReservationUseCase.expirePendingApprovals()`.

B. Obal otevře transakci; `ReservationService` načte čas serveru z `Clock` a zavolá `ReservationRepository.expirePending(now)`, tedy jedinou podmíněnou aktualizaci. Transakce se potvrdí.

C. Stejná operace se provádí ve vlastní potvrzené transakci také před operacemi Schválit, Zamítnout, Potvrdit a Zrušit a před výpisem rezervací nebo čekajících schválení.

D. Pokud databáze není dostupná, aktualizace se odloží na další spuštění; rozhodovací operace i tak samy ověřují aktuální čas.

### Důležité metody / operace

**Operace:** `ReservationService.approveReservation(id, userId)`

* **Odpovědnost:** Koordinuje rozhodnutí: oprávnění, zámek, stav, konflikt, doménový přechod a uložení.
* **Vstup:** `Long id`, `String userId`.
* **Výstup:** `Reservation` odrážející aktualizovaný stav.
* **Vedlejší účinky:** Aktualizuje databázi; vyžádá odeslání oznámení po potvrzení transakce.
* **Chování při chybě:** Vyvolá `UnauthorizedReservationException` (403), `ReservationNotFoundException` (404), `InvalidReservationStateException` (409) nebo `ReservationBusinessRuleException` (409); transakce se vrátí zpět a nic se nezmění.

**Operace:** `Reservation.approve(resource, now)`

* **Odpovědnost:** Vynucuje čistá doménová pravidla přechodu.
* **Vstup:** `Resource resource`, `LocalDateTime now` (UTC).
* **Výstup:** Žádný (mění interní stav).
* **Předpoklady:** Stav je `PENDING_APPROVAL` a platí `now < startTime`.
* **Následné podmínky:** Stav je `CONFIRMED`.
* **Chování při chybě:** `InvalidReservationStateException`, pokud stav není čekající; `ReservationBusinessRuleException`, pokud platí `now >= startTime` nebo `participantCount > resource.capacity`; stav zůstane beze změny.

**Operace:** `ReservationService.expirePendingApprovals()`

* **Odpovědnost:** Zjišťuje aktuální čas a vyžádá podmíněnou expiraci.
* **Vedlejší účinky:** Aktualizuje pouze zastaralé řádky ve stavu `PENDING_APPROVAL`; neodesílá oznámení.
* **Chování při chybě:** Chyba perzistence se předá volajícímu; časovač operaci zkusí znovu při dalším spuštění.

### Algoritmus / rozhodovací logika

```text
function approveReservation(id, userId):            // obal v BeanConfiguration
    expirePendingApprovals()                        // vlastní transakce, zde již potvrzená

    inTransaction:                                  // transakce rozhodnutí
        requireApprover(userId)                     // 403
        reservation = repository.findByIdForUpdate(id)       // zámek řádku, 404
        resource = resourceRepository.findById(reservation.resourceId)   // 404

        if reservation.state != PENDING_APPROVAL:
            throw InvalidState                      // 409

        if overlapWithConfirmed(reservation):       // ignoruje samotnou rezervaci
            throw BusinessRule                      // 409, požadavek zůstává čekající

        reservation.approve(resource, now)          // now < start, kapacita, -> CONFIRMED
        repository.save(reservation)                // omezení EXCLUDE je poslední pojistka
        notifyConfirmed(reservation) afterCommit    // při selhání se chyba ignoruje

    return reservation

function expirePendingApprovals():
    repository.expirePending(now)                   // UPDATE state = 'EXPIRED'
                                                    // WHERE state = 'PENDING_APPROVAL' AND start <= now
```

Kontrola oprávnění probíhá uvnitř transakce rozhodnutí, až po hromadné expiraci. Hromadná expirace závisí pouze na čase, je idempotentní a nemění žádnou rezervaci jinak, než by ji změnil časovač. Neoprávněný volající ji tedy může spustit bez škodlivých následků.

### Rozhodnutí podrobného návrhu záměrně ponechaná implementaci

Zaznamenáno jako implementované:

* **Porušení omezení → 409:** `RestExceptionHandler.handleConstraint` převádí `DataIntegrityViolationException` na `409 BUSINESS_RULE_VIOLATION` se zprávou o překryvu. V současnosti zpracovává všechna porušení integrity stejným způsobem.
* **Interval časovače:** `@Scheduled(fixedDelay = 1000)`, tedy jednu sekundu po dokončení předchozího spuštění; `reservation.expiry.enabled=false` časovač vypne (testy pak volají stejnou operaci s řízenými hodinami). Interval je technické rozhodnutí, obchodní hranicí je začátek rezervace.
* **Formát logu `LoggingNotificationAdapter`:** `OZNÁMENÍ: rezervace {id} uživatele {userId} byla potvrzena`.
