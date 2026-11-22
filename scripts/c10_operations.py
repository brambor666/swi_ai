"""C10 operations experiment. Only a newly created disposable database is modified."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import socket
import subprocess
import time
import urllib.error
import urllib.request
import uuid
import sys
sys.stdout.reconfigure(encoding="utf-8")

parser = argparse.ArgumentParser()
parser.add_argument("--jar", required=True)
parser.add_argument("--commit", required=True, help="Commit used to build the JAR")
args = parser.parse_args()
jar = Path(args.jar).resolve()
if not jar.is_file():
    raise SystemExit("Build the specified JAR first")
run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
out = Path(__file__).resolve().parents[1] / "artifacts" / "c10" / run_id
out.mkdir(parents=True)
container = "swi-c10-" + uuid.uuid4().hex[:10]
result = {"commit": args.commit, "jarSha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
          "runUtc": run_id, "container": container, "events": [], "verdict": "NOT RUN"}
app = None
app_log = None
created = False

def event(kind, **data):
    item = {"timeUtc": dt.datetime.now(dt.timezone.utc).isoformat(), "event": kind, **data}
    result["events"].append(item)
    print(json.dumps(item, ensure_ascii=False), flush=True)
    (out / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

def docker(*arguments):
    return subprocess.check_output(["docker", *arguments], text=True, encoding="utf-8").strip()

def sql(query):
    return docker("exec", container, "psql", "-U", "rezervace", "-d", "rezervace", "-At", "-c", query)

def db_ready():
    for _ in range(45):
        if subprocess.run(["docker", "exec", container, "pg_isready", "-U", "rezervace"],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
            return
        time.sleep(1)
    raise RuntimeError("DB readiness timeout")

def request(method, path, body=None):
    payload = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base + path, data=payload, method=method,
                                 headers={"X-User-Id": "c10-test", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())

def start_app(number):
    global app, app_log
    app_log = (out / f"application-{number}.log").open("w", encoding="utf-8")
    app = subprocess.Popen(["java", "-jar", str(jar), f"--server.port={app_port}",
                            f"--spring.datasource.url=jdbc:postgresql://localhost:{db_port}/rezervace",
                            "--spring.datasource.hikari.connection-timeout=2000",
                            "--spring.datasource.hikari.validation-timeout=1000"],
                           stdout=app_log, stderr=subprocess.STDOUT)
    event("application_process_started", pid=app.pid, log=app_log.name)
    for _ in range(50):
        if app.poll() is not None:
            raise RuntimeError("Application exited before readiness")
        try:
            code, body = request("GET", "/actuator/health")
            if code == 200 and body["status"] == "UP":
                event("application_ready", http=code)
                return
        except (OSError, ValueError):
            pass
        time.sleep(1)
    raise RuntimeError("Application readiness timeout")

def stop_app():
    global app, app_log
    if app is not None:
        pid = app.pid
        app.terminate()
        app.wait(timeout=20)
        event("application_process_stopped", pid=pid, exitCode=app.returncode,
              method="Popen.terminate; on Windows this is forced process termination")
        app = None
        app_log.close()
        app_log = None

def create(start, end):
    return request("POST", "/reservations", {"resourceId": room, "userId": "c10-test",
                   "start": start.isoformat(), "end": end.isoformat(), "participantCount": 10})

def persisted():
    return json.loads(sql("SELECT COALESCE(json_agg(r ORDER BY id),'[]') FROM "
                          "(SELECT id,resource_id,start_time,end_time,state FROM reservation) r"))

try:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        chosen_db_port = sock.getsockname()[1]
    docker("run", "-d", "--name", container, "-e", "POSTGRES_DB=rezervace",
           "-e", "POSTGRES_USER=rezervace", "-e", "POSTGRES_PASSWORD=rezervace",
           "-p", f"127.0.0.1:{chosen_db_port}:5432", "postgres:16")
    created = True
    db_port = docker("port", container, "5432").rsplit(":", 1)[1]
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        app_port = sock.getsockname()[1]
    base = f"http://localhost:{app_port}"
    event("isolated_database_created", dbPort=db_port, appPort=app_port,
          config={"connectionTimeoutMs": 2000, "validationTimeoutMs": 1000, "expiryEnabled": True})
    db_ready()
    start_app(1)
    event("database_version", version=sql("SHOW server_version"))
    room = int(sql("SELECT id FROM resource WHERE NOT requires_approval ORDER BY id LIMIT 1"))
    t = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=2)).replace(
        hour=10, minute=0, second=0, microsecond=0, tzinfo=None)
    code, body = create(t, t + dt.timedelta(hours=1))
    assert code == 201 and body["state"] == "DRAFT", (code, body)
    first = body["id"]
    code, body = request("POST", f"/reservations/{first}/confirm")
    assert code == 200 and body["state"] == "CONFIRMED", (code, body)
    rows = persisted()
    assert len(rows) == 1 and rows[0]["id"] == first and rows[0]["state"] == "CONFIRMED"
    assert rows[0]["resource_id"] == room
    assert dt.datetime.fromisoformat(rows[0]["start_time"]) == t
    assert dt.datetime.fromisoformat(rows[0]["end_time"]) == t + dt.timedelta(hours=1)
    event("success", http=code, rows=rows, verdict="PASS")

    code, body = create(t + dt.timedelta(minutes=30), t + dt.timedelta(minutes=90))
    assert code == 201
    second = body["id"]
    code, body = request("POST", f"/reservations/{second}/confirm")
    assert code == 409 and body["code"] == "BUSINESS_RULE_VIOLATION", (code, body)
    rows = persisted()
    assert [r["state"] for r in rows] == ["CONFIRMED", "DRAFT"]
    overlaps = sql("SELECT count(*) FROM reservation a JOIN reservation b "
                   "ON a.id<b.id AND a.resource_id=b.resource_id AND a.start_time<b.end_time "
                   "AND b.start_time<a.end_time WHERE a.state='CONFIRMED' AND b.state='CONFIRMED'")
    assert overlaps == "0"
    event("collision", http=code, response=body, rows=rows, confirmedOverlaps=0, verdict="PASS")

    before = rows
    docker("stop", "--time", "2", container)
    event("database_stopped")
    code, body = create(t + dt.timedelta(hours=2), t + dt.timedelta(hours=3))
    event("outage_attempt_once", http=code, response=body)
    assert 500 <= code < 600, (code, body)
    docker("start", container)
    restored_port = docker("port", container, "5432").rsplit(":", 1)[1]
    event("database_port_after_restart", before=db_port, after=restored_port)
    assert restored_port == db_port
    db_ready()
    rows = persisted()
    assert rows == before, rows
    event("database_restored_state_checked_before_any_new_write", rows=rows)
    for _ in range(20):
        code, body = request("GET", "/actuator/health")
        if code == 200: break
        time.sleep(1)
    assert code == 200, (code, body)
    # A distinct request after authoritative reconciliation, not a blind retry.
    code, body = create(t + dt.timedelta(hours=4), t + dt.timedelta(hours=5))
    assert code == 201
    third = body["id"]
    code, body = request("POST", f"/reservations/{third}/confirm")
    assert code == 200
    before_restart = persisted()
    assert [r["state"] for r in before_restart] == ["CONFIRMED", "DRAFT", "CONFIRMED"]
    event("outage_recovery", http=code, rows=before_restart, verdict="PASS")

    stop_app()
    start_app(2)
    rows = persisted()
    assert rows == before_restart
    code, body = request("GET", "/reservations")
    assert code == 200 and any(r["id"] == first and r["state"] == "CONFIRMED" for r in body)
    code, body = create(t + dt.timedelta(hours=6), t + dt.timedelta(hours=7))
    assert code == 201
    fourth = body["id"]
    code, body = request("POST", f"/reservations/{fourth}/confirm")
    assert code == 200
    rows = persisted()
    assert len(rows) == 4 and rows[-1]["state"] == "CONFIRMED"
    event("restart_and_next_operation", http=code, rows=rows, verdict="PASS")
    result["verdict"] = "PASS"
except Exception as error:
    result["verdict"] = "FAIL"
    event("experiment_failed", error=type(error).__name__, detail=str(error))
    raise
finally:
    stop_app()
    if created:
        docker("rm", "-f", container)
        event("isolated_database_removed", container=container)
    (out / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Evidence:", out, flush=True)
