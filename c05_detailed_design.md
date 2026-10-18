## C05 Detailed Design

**Selected architecture unit:**
Reservation Approval and Expiration workflow (Reservation Management).

**Related requirement(s):**
REQ-06–08, REQ-14, REQ-16.

**Related scenario(s):**
OP-05 — Approve Reservation, OP-08 — Expire Pending Approval.

**Relevant business rule(s):**
BR-02 (Allocation), BR-03 (Capacity), BR-05 (Permissions), BR-06 (Rejection/Concurrency), BR-07 (Notifications), BR-09 (Request Validity).

### Detailed responsibilities

| Detailed responsibility | Why is it needed? | 
| ----- | ----- | 
| Verify approver permissions | Ensures only authorized administrators (checked against server configuration) can approve requests. | 
| Execute pre-operation expiration | Prevents race conditions where a request is approved exactly as it expires by updating `EXPIRED` states before evaluating the approval. | 
| Acquire pessimistic row lock | Prevents concurrent modifications (e.g., simultaneous Approve, Reject, or Cancel) on the same pending request. | 
| Evaluate domain invariants | Ensures the reservation is still in `PENDING_APPROVAL`, the required capacity is met, and the server time is strictly before the start time (`now < start`). | 
| Detect conflicting allocations | Validates that no other `CONFIRMED` reservation occupies the same resource during the requested interval to uphold BR-02. | 
| Execute state transition | Atomically transitions the reservation to `CONFIRMED` and saves it to PostgreSQL. | 
| Dispatch notification | Triggers the `NotificationPort` strictly after the database commit completes, ensuring failures do not roll back the approval. | 

### Design elements and ownership

| Responsibility | Owner | State/data | Why here? | 
| ----- | ----- | ----- | ----- | 
| Orchestration & Permissions | `ReservationService` | Approver ID list, `ReservationUseCase` interface | Coordinates the transaction, checks authorization, and invokes domain logic. | 
| Pre-operation Expiration | `ReservationService` | Server clock, `PENDING_APPROVAL` status | Evaluates the time threshold independently of the specific reservation request. | 
| Domain Invariants | `Reservation` (Entity) | `state`, `startTime`, `participantCount` | The domain entity acts as the absolute source of truth for its lifecycle transitions and rules. | 
| Resource Constraints | `Resource` (Entity) | `capacity` | Resource holds its own physical limitations. | 
| Persistence & Locking | `ReservationRepository` | PostgreSQL row locks, `EXCLUDE USING gist` | Manages data retrieval (`findByIdForUpdate`), concurrent locking, and conflict queries. | 
| External Messaging | `NotificationPort` | Log output | Isolates external notification boundaries from core domain logic. | 

### Key interface(s) / contract(s)

```
<<interface>> ReservationUseCase

ReservationResult approveReservation(UUID id, String userId);
void expirePendingApprovals();

```

**Contract for `approveReservation`:**

* **Preconditions:**

  * `userId` matches an authorized approver.

  * `id` corresponds to an existing reservation.

* **Postconditions:**

  * Reservation state transitions from `PENDING_APPROVAL` to `CONFIRMED`.

  * Resource capacity accommodates `participantCount`.

  * No overlapping `CONFIRMED` reservation exists for the same resource.

  * Notification dispatch is requested post-commit.

* **Failures:**

  * `401/403` if identity is missing or unauthorized.

  * `404` if the reservation or resource is unknown.

  * `409 Conflict` if the reservation is not pending, exceeds capacity, overlaps with a confirmed reservation, or `now >= start`.

### Focused structural view

```
[ReservationController] 
        │
        ▼
<<interface>> [ReservationUseCase]
        ▲
        │
[ReservationService] ────► [Reservation] ────► [Resource]
        │                        │
        │                        ▼
        │                  <<interface>> [ResourceRepository]
        │
        ├────────────────► <<interface>> [ReservationRepository]
        │
        └────────────────► <<interface>> [NotificationPort]

```

### Detailed sequence

 1. `ReservationController` receives approval request and caller ID.

 2. `ReservationService.approveReservation` is wrapped in an independent transaction block.

 3. `ReservationService` executes `expirePendingApprovals()` to sweep globally expired requests.

 4. `ReservationService` validates the caller's approver identity.

 5. `ReservationRepository.findByIdForUpdate` fetches the target reservation and acquires a pessimistic row lock.

 6. `ResourceRepository` fetches the associated resource.

 7. `ReservationService.hasOverlapWithConfirmedReservations` queries the repository for conflicting `CONFIRMED` allocations.

 8. `Reservation.approve(resource, now)` validates capacity and time, transitioning state to `CONFIRMED`.

 9. `ReservationRepository.save` executes the update (guarded by PostgreSQL `no_overlapping_confirmed_reservations` constraint).

10. The transaction commits, releasing the lock.

11. The transaction manager triggers `NotificationPort.notifyConfirmed`.

### Important methods / operations

**Operation:** `ReservationService.approveReservation(id, userId)`

* **Responsibility:** Orchestrates the approval workflow, locking, and persistence.

* **Input:** `UUID id`, `String userId`.

* **Output:** `Reservation` object reflecting the updated state.

* **Side effects:** Updates database state; triggers external notification post-commit.

* **Failure behavior:** Throws `ReservationBusinessRuleException` on domain violations, initiating a rollback.

**Operation:** `Reservation.approve(resource, now)`

* **Responsibility:** Enforces pure domain logic for approval transitions.

* **Input:** `Resource resource`, `Instant now`.

* **Output:** None (mutates internal state).

* **Preconditions:** Current state is `PENDING_APPROVAL`.

* **Postconditions:** Current state is `CONFIRMED`.

* **Failure behavior:** Throws domain exceptions if `now >= startTime` or `participantCount > resource.capacity`.

### Algorithm / decision logic

```
function approveReservation(id, userId):
    requireApprover(userId)
    
    // Explicit expiration sweep prior to target evaluation
    expirePendingApprovals()
    
    // Acquire pessimistic row lock
    reservation = repository.findByIdForUpdate(id)
    if not reservation: throw NotFound
    
    resource = resourceRepository.findById(reservation.resourceId)
    
    if reservation.state != PENDING_APPROVAL:
        throw Conflict("Only pending reservations can be approved")
        
    // Validate concurrency constraint
    overlap = repository.findConfirmedByResourceAndTimeRange(
        reservation.resourceId, reservation.startTime, reservation.endTime)
    if overlap exists:
        throw Conflict("Resource is already booked for this interval")
        
    // Evaluate domain invariants
    reservation.approve(resource, currentTime())
    
    repository.save(reservation) // relies on DB constraint for ultimate consistency
    return reservation

```

### DD decisions intentionally left to code

* The exact translation of `EXCLUDE USING gist` PostgreSQL constraint violations into specific HTTP 409 responses via `RestExceptionHandler`.

* The exact scheduling cron expression or interval for the autonomous `ApprovalExpiry` timer.

* Specific string formatting for the `LoggingNotificationAdapter` output.