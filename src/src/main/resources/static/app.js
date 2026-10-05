const STORAGE_KEY = 'reservation.userId';
const STATE_LABELS = { DRAFT: 'Návrh', CONFIRMED: 'Potvrzená', CANCELLED: 'Zrušená', PENDING_APPROVAL: 'Čeká na schválení', REJECTED: 'Zamítnutá', EXPIRED: 'Vypršela' };

const $ = (id) => document.getElementById(id);
let resourcesById = new Map();

// ---------- čas: uživatel pracuje v místním čase, API v UTC (BR-01) ----------

function toUtcIso(localValue) {
    return new Date(localValue).toISOString().slice(0, 19);
}

function formatUtc(utcValue) {
    return new Date(utcValue + 'Z').toLocaleString('cs-CZ', { dateStyle: 'medium', timeStyle: 'short' });
}

function toLocalInput(date) {
    const p = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}T${p(date.getHours())}:${p(date.getMinutes())}`;
}

function setDefaultSlot() {
    const start = new Date();
    start.setDate(start.getDate() + 1);
    start.setHours(10, 0, 0, 0);
    $('start').value = toLocalInput(start);
    $('end').value = toLocalInput(new Date(start.getTime() + 60 * 60 * 1000));
}

// ---------- API ----------

function currentUser() {
    return $('userId').value.trim();
}

async function api(method, path, body) {
    const headers = { 'X-User-Id': currentUser() };
    if (body) headers['Content-Type'] = 'application/json';

    const response = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined });
    const text = await response.text();
    let data = null;
    if (text) {
        try { data = JSON.parse(text); } catch { data = null; }
    }

    if (!response.ok) {
        const error = new Error(data?.message ?? `Požadavek selhal`);
        error.status = response.status;
        throw error;
    }
    return data;
}

// ---------- zprávy ----------

function showMessage(text, kind = 'info') {
    const box = $('message');
    box.textContent = text;
    box.className = `message ${kind}`;
    box.hidden = false;
}

/** Spustí akci a případnou chybu ukáže uživateli (včetně důvodu odmítnutí z API). */
async function guarded(action) {
    try {
        await action();
    } catch (error) {
        if (error.status) {
            showMessage(`${error.message} (HTTP ${error.status})`, 'error');
        } else {
            showMessage(error.message || 'Server neodpovídá.', 'error');
        }
    }
}

// ---------- načítání dat ----------

async function loadResources() {
    const resources = await api('GET', '/resources');
    resourcesById = new Map(resources.map((r) => [r.id, r]));
    $('resourceId').replaceChildren(
        ...resources.map((r) => new Option(`${r.label} (kapacita ${r.capacity})${r.requiresApproval ? " — vyžaduje schválení" : ""}`, r.id)));
}

async function loadReservations() {
    renderReservations(await api('GET', '/reservations'));
}

function cell(text) {
    const td = document.createElement('td');
    td.textContent = text;
    return td;
}

function actionButton(label, onClick, extraClass = '') {
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = label;
    button.className = `small ${extraClass}`.trim();
    button.addEventListener('click', () => guarded(onClick));
    return button;
}

function buildRow(reservation) {
    const row = document.createElement('tr');
    const resource = resourcesById.get(reservation.resourceId);

    const badge = document.createElement('span');
    badge.className = `badge ${reservation.state.toLowerCase()}`;
    badge.textContent = STATE_LABELS[reservation.state] ?? reservation.state;
    const stateCell = document.createElement('td');
    stateCell.append(badge);

    const actions = document.createElement('td');
    if (reservation.state === 'DRAFT') {
        actions.append(actionButton('Potvrdit', () => confirmReservation(reservation.id)));
    }
    if (reservation.state === 'DRAFT' || reservation.state === 'CONFIRMED' || reservation.state === 'PENDING_APPROVAL') {
        actions.append(actionButton('Zrušit', () => cancelReservation(reservation.id), 'danger'));
    }

    row.append(
        cell(resource ? resource.label : `Učebna ${reservation.resourceId}`),
        cell(formatUtc(reservation.start)),
        cell(formatUtc(reservation.end)),
        cell(String(reservation.participantCount)),
        stateCell,
        actions);
    return row;
}

function renderReservations(reservations) {
    const body = $('reservationsBody');
    body.replaceChildren();

    if (reservations.length === 0) {
        const row = document.createElement('tr');
        const empty = cell('Zatím nemáš žádné rezervace.');
        empty.colSpan = 6;
        empty.className = 'empty';
        row.append(empty);
        body.append(row);
        return;
    }
    reservations.forEach((reservation) => body.append(buildRow(reservation)));
}

// ---------- akce ----------

function readForm() {
    const resourceId = Number($('resourceId').value);
    const start = $('start').value;
    const end = $('end').value;
    if (!resourceId || !start || !end) {
        throw new Error('Vyber učebnu a vyplň začátek i konec.');
    }
    return {
        resourceId,
        start: toUtcIso(start),
        end: toUtcIso(end),
        participantCount: Number($('participants').value),
    };
}

async function checkAvailability() {
    const form = readForm();
    const query = `start=${encodeURIComponent(form.start)}&end=${encodeURIComponent(form.end)}`;
    const result = await api('GET', `/resources/${form.resourceId}/availability?${query}`);
    if (result.available) {
        showMessage('Termín je volný.', 'ok');
    } else {
        showMessage('Termín je obsazený potvrzenou rezervací.', 'warn');
    }
}

async function createReservation() {
    const form = readForm();
    const created = await api('POST', '/reservations', { ...form, userId: currentUser() });
    showMessage(`Návrh byl vytvořen (ID ${created.id}). Potvrď ho v tabulce níže.`, 'ok');
    await loadReservations();
}

async function confirmReservation(id) {
    const result = await api('POST', `/reservations/${id}/confirm`);
    showMessage(result.state === 'PENDING_APPROVAL' ? 'Žádost čeká na správce. Učebna ještě není rezervovaná.' : 'Rezervace byla potvrzena.', 'ok');
    await loadReservations();
}

async function cancelReservation(id) {
    if (!window.confirm('Opravdu zrušit tuto rezervaci?')) return;
    await api('POST', `/reservations/${id}/cancel`);
    showMessage('Rezervace byla zrušena.', 'ok');
    await loadReservations();
}

async function loadAll() {
    await loadResources();
    await loadReservations();
}

// ---------- start ----------

$('userId').value = localStorage.getItem(STORAGE_KEY) ?? 'user-1';
$('tz').textContent = Intl.DateTimeFormat().resolvedOptions().timeZone;
setDefaultSlot();

$('useUser').addEventListener('click', () => guarded(async () => {
    localStorage.setItem(STORAGE_KEY, currentUser());
    $('message').hidden = true;
    $('approvalsBody').replaceChildren();
    await loadAll();
}));
$('checkAvailability').addEventListener('click', () => guarded(checkAvailability));
$('refresh').addEventListener('click', () => guarded(loadReservations));
$('reservationForm').addEventListener('submit', (event) => {
    event.preventDefault();
    guarded(createReservation);
});

guarded(loadAll);

async function loadApprovals() {
    const items = await api('GET', '/reservations/pending-approvals');
    const body = $('approvalsBody');
    body.replaceChildren();
    for (const item of items) {
        const row = document.createElement('tr');
        const actions = document.createElement('td');
        for (const [label, operation] of [['Schválit', 'approve'], ['Zamítnout', 'reject']]) {
            actions.append(actionButton(label, async () => {
                try {
                    await api('POST', `/reservations/${item.id}/${operation}`);
                    showMessage(operation === 'approve' ? 'Rezervace schválena.' : 'Žádost zamítnuta.', 'ok');
                } finally {
                    await loadApprovals();
                    await loadReservations();
                }
            }));
        }
        row.append(cell(String(item.id)), cell(item.userId),
            cell(resourcesById.get(item.resourceId)?.label ?? String(item.resourceId)),
            cell(formatUtc(item.start)), cell(String(item.participantCount)), actions);
        body.append(row);
    }
    if (!items.length) {
        const row = document.createElement('tr');
        const td = cell('Žádné čekající žádosti.'); td.colSpan = 6; row.append(td); body.append(row);
    }
}
$('loadApprovals').addEventListener('click', () => guarded(loadApprovals));
