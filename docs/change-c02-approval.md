# Dopad změny C02 — schvalovací proces

Analýza dopadu před úpravou specifikace a implementace v0.2.
Zvolená pravidla: běžné učebny automaticky, speciální prostory schvaluje správce; čekání neblokuje dostupnost; konec platnosti je začátek rezervace.

| Oblast | Dopad |
|---|---|
| Create / REQ-01–02 | Beze změny: vytváří DRAFT bez alokace. |
| Availability / REQ-03–04, BR-01–02 | Beze změny: blokuje jen CONFIRMED, včetně při čekání, zamítnutí a vypršení. |
| Confirm / REQ-05–08 | U speciálních učeben pouze odešle žádost do PENDING_APPROVAL; bez oznámení potvrzení a bez kontroly kolize. Běžné učebny beze změny. |
| Cancel / REQ-09–12, BR-04 | Nově lze zrušit PENDING_APPROVAL před začátkem. DRAFT nadále kdykoli; CONFIRMED nadále do start − 2 h včetně. |
| Nové cíle | Správce prostor zobrazí čekající žádosti a schválí nebo zamítne. |
| Stavy | Přibývají PENDING_APPROVAL, REJECTED a EXPIRED; pouze CONFIRMED alokuje. |
| Čas | Čekající žádost vyprší při now >= start, žádná další libovolná lhůta. |
| Ověření | Čekání, znovuověření kolize a kapacity, oprávnění, zamítnutí, hranice expirace, zrušení, souběh rozhodnutí. |
| Diagramy | Je potřeba doplnit nové stavy a aktéra správce. Aktualizace zatím není hotová. |
| C03 drivery | Perzistentní čekající proces, obnova po restartu, časová expirace, autorizace schvalovatele a atomické rozhodnutí. |

Identita zůstává lokálně simulovaná X-User-Id. Schvalovatelé jsou povoleni serverovou konfigurací (výchozí admin), nikoli rolí zaslanou klientem. Produkční Identity Provider a Notification Service zůstávají předpoklady prototypu. Nové povinné notifikace zamítnutí nebo vypršení se nezavádějí.
