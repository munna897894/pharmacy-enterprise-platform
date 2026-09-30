#!/usr/bin/env python3
"""Project-scoped, host-native MySQL 8.4 for local cluster testing.

State lives under the gitignored .local/ directory. The existing host server
(3306) and Compose server (3307) are never contacted. No credential is passed on
a command line, printed, or written outside mode-0600 files under .local/.
"""
import argparse
import os
import pathlib
import re
import secrets
import shutil
import signal
import socket
import stat
import subprocess
import sys
import tempfile
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEMPLATE = ROOT / "infra/local/mysql/my.cnf.template"
COMPOSE_SQL = ROOT / "infra/compose/mysql-init/01-create-schemas.sql"
DATA_DIR = ROOT / ".local/mysql-data"
STATE_DIR = ROOT / ".local/mysql"
RUN_DIR = STATE_DIR / "run"
LOG_DIR = STATE_DIR / "log"
TMP_DIR = STATE_DIR / "tmp"
SERVER_CNF = STATE_DIR / "my.cnf"
ADMIN_CNF = STATE_DIR / "admin.cnf"
SOCKET = RUN_DIR / "mysqld.sock"
PID_FILE = RUN_DIR / "mysqld.pid"
ERROR_LOG = LOG_DIR / "mysqld.err"

DEFAULT_PORT = 3308
RESERVED_PORTS = {3306, 3307}
BASEDIR_CANDIDATES = ("/usr/local/opt/mysql@8.4", "/opt/homebrew/opt/mysql@8.4")
IDENTIFIER = re.compile(r"^[A-Za-z0-9_]{1,32}$")
SCHEMA_PRIVILEGES = ("SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, "
                     "REFERENCES, CREATE VIEW, SHOW VIEW, LOCK TABLES")

# (env prefix, default schema, default user) — mirrors .env.example and the
# Compose init SQL; `check_inventory` fails fast if they drift.
SERVICES = (
    ("AUTH", "auth_service", "auth_user"),
    ("PRODUCT", "pharmacy_product", "product_user"),
    ("INVENTORY", "pharmacy_inventory", "inventory_user"),
    ("ORDER", "pharmacy_order", "order_user"),
    ("PAYMENT", "pharmacy_payment", "payment_user"),
    ("AUDIT", "pharmacy_audit", "audit_user"),
    ("CUSTOMER", "pharmacy_customer", "customer_user"),
    ("PHARMACY", "pharmacy_pharmacy", "pharmacy_user"),
    ("PRESCRIPTION", "prescription_service", "prescription_user"),
    ("NOTIFICATION", "pharmacy_notification", "pharmacy_notification"),
)


USAGE = """commands:
  init       bootstrap datadir (first run), start, provision and verify (idempotent)
  start      bootstrap datadir on first run, then start in the background
  stop       graceful shutdown; data is preserved
  status     exit 0 when running and answering
  provision  create/update the 10 schemas and schema-scoped users from the env file
  verify     log in as every service user; check own-schema DDL/DML and cross-schema denial

environment:
  MYSQL_LOCAL_ENV_FILE / K8S_ENV_FILE  env file (same file k8s-local-secret.sh reads)
  MYSQL_LOCAL_PORT                     default 3308; 3306/3307 are refused
  MYSQL_LOCAL_BIND_ADDRESS             default 127.0.0.1
  MYSQL_LOCAL_BASEDIR                  default: brew --prefix mysql@8.4

The default env file is .env.example (demo credentials), matching the guide's
`K8S_ENV_FILE=.env.example sh scripts/k8s-local-secret.sh`; .env is never read
unless selected and never modified. Custom credentials:
  K8S_ENV_FILE=.env sh scripts/local-mysql-init.sh
  K8S_ENV_FILE=.env sh scripts/k8s-local-secret.sh
"""


def fail(message):
    print(f"local-mysql: {message}", file=sys.stderr)
    sys.exit(1)


def info(message):
    print(f"local-mysql: {message}", flush=True)


def port():
    value = os.environ.get("MYSQL_LOCAL_PORT", str(DEFAULT_PORT))
    if not value.isdigit() or int(value) in RESERVED_PORTS or not 1024 <= int(value) <= 65535:
        fail(f"MYSQL_LOCAL_PORT={value!r} is invalid or reserved for existing servers (3306/3307).")
    return int(value)


def bind_address():
    value = os.environ.get("MYSQL_LOCAL_BIND_ADDRESS", "127.0.0.1")
    if not re.fullmatch(r"[0-9.:]+", value):
        fail("MYSQL_LOCAL_BIND_ADDRESS must be a literal IP address.")
    return value


def basedir():
    candidates = [os.environ["MYSQL_LOCAL_BASEDIR"]] if os.environ.get("MYSQL_LOCAL_BASEDIR") else []
    if shutil.which("brew"):
        result = subprocess.run(["brew", "--prefix", "mysql@8.4"], capture_output=True, text=True)
        if result.returncode == 0:
            candidates.append(result.stdout.strip())
    candidates.extend(BASEDIR_CANDIDATES)
    for candidate in candidates:
        path = pathlib.Path(candidate)
        if (path / "bin/mysqld").is_file() and (path / "bin/mysql").is_file():
            resolved = path.resolve()
            if str(resolved).startswith("/usr/local/mysql"):
                fail("Refusing to use the shared /usr/local/mysql installation.")
            return path
    fail("MySQL 8.4 not found; run `brew install mysql@8.4` or set MYSQL_LOCAL_BASEDIR.")


def binary(name):
    return str(basedir() / "bin" / name)


def check_version():
    output = subprocess.run([binary("mysqld"), "--no-defaults", "--version"],
                            capture_output=True, text=True, check=True).stdout
    if not re.search(r"Ver 8\.4\.", output):
        fail(f"Expected MySQL 8.4, found: {output.strip()}")


def private_dir(path):
    path.mkdir(parents=True, exist_ok=True)
    path.chmod(0o700)


def write_private(path, content):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as handle:
        handle.write(content)
    path.chmod(0o600)


def ensure_private_file(path):
    mode = stat.S_IMODE(path.stat().st_mode)
    if mode & 0o077:
        fail(f"{path.relative_to(ROOT)} must not be group/world accessible (mode {mode:o}).")


def render_config():
    for directory in (STATE_DIR, RUN_DIR, LOG_DIR, TMP_DIR):
        private_dir(directory)
    rendered = TEMPLATE.read_text()
    for key, value in {"DATA_DIR": DATA_DIR, "RUN_DIR": RUN_DIR, "LOG_DIR": LOG_DIR,
                       "TMP_DIR": TMP_DIR, "PORT": port(), "BIND_ADDRESS": bind_address()}.items():
        rendered = rendered.replace(f"@{key}@", str(value))
    if "@" in rendered:
        fail("Unrendered placeholder in MySQL config template.")
    write_private(SERVER_CNF, rendered)


def sql_string(value):
    if "\x00" in value or "\n" in value:
        fail("Credentials must not contain NUL or newline characters.")
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"


def cnf_value(value):
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def is_initialized():
    return (DATA_DIR / "mysql.ibd").exists()


def pid():
    try:
        value = int(PID_FILE.read_text().strip())
        os.kill(value, 0)
    except (FileNotFoundError, ValueError, ProcessLookupError, PermissionError):
        return None
    command = subprocess.run(["ps", "-p", str(value), "-o", "command="], capture_output=True, text=True).stdout
    return value if "mysqld" in command and str(SERVER_CNF) in command else None


def port_in_use(number):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.settimeout(1)
        return probe.connect_ex(("127.0.0.1", number)) == 0


def admin_client(*args, sql=None, check=True):
    ensure_private_file(ADMIN_CNF)
    result = subprocess.run([binary("mysql"), f"--defaults-file={ADMIN_CNF}", "--protocol=SOCKET",
                             "--batch", "--skip-column-names", *args],
                            input=sql, capture_output=True, text=True)
    if check and result.returncode != 0:
        fail(f"admin SQL failed: {redact(result.stderr)}")
    return result


def redact(text):
    return re.sub(r"(?i)(identified by\s+)'[^']*'", r"\1'***'", text.strip())


def ping():
    if not ADMIN_CNF.exists() or not SOCKET.exists():
        return False
    return admin_client("-e", "SELECT 1", check=False).returncode == 0


def bootstrap():
    if is_initialized():
        return
    if DATA_DIR.exists() and any(DATA_DIR.iterdir()):
        fail(f"{DATA_DIR.relative_to(ROOT)} is non-empty but not an initialized MySQL datadir; "
             "inspect it manually. It will not be deleted.")
    private_dir(DATA_DIR)
    password = secrets.token_urlsafe(32)
    write_private(ADMIN_CNF, f"[client]\nuser=root\npassword={cnf_value(password)}\n"
                             f"socket={SOCKET}\n")
    init_file = TMP_DIR / f"init-{secrets.token_hex(8)}.sql"
    try:
        write_private(init_file, f"ALTER USER 'root'@'localhost' IDENTIFIED BY {sql_string(password)};\n")
        info(f"initializing new datadir {DATA_DIR.relative_to(ROOT)}")
        result = subprocess.run([binary("mysqld"), f"--defaults-file={SERVER_CNF}", "--initialize-insecure",
                                 f"--init-file={init_file}"], capture_output=True, text=True)
    finally:
        init_file.unlink(missing_ok=True)
    if result.returncode != 0:
        fail(f"mysqld --initialize failed; see {ERROR_LOG.relative_to(ROOT)}")
    info(f"admin credentials generated in {ADMIN_CNF.relative_to(ROOT)} (mode 0600)")


def start(wait_seconds=60):
    check_version()
    render_config()
    bootstrap()
    if pid() and ping():
        info(f"already running (pid {pid()}) on {bind_address()}:{port()}")
        return
    if pid():
        fail(f"process {pid()} owns {PID_FILE.relative_to(ROOT)} but is not answering; run stop first.")
    if port_in_use(port()):
        fail(f"port {port()} is already in use by another process; not starting.")
    if not ADMIN_CNF.exists():
        fail(f"initialized datadir exists but {ADMIN_CNF.relative_to(ROOT)} is missing; "
             "restore it or recover the root password manually.")
    PID_FILE.unlink(missing_ok=True)
    with open(os.devnull, "rb") as devnull_in, open(LOG_DIR / "mysqld.stdout", "ab") as out:
        subprocess.Popen([binary("mysqld"), f"--defaults-file={SERVER_CNF}"], stdin=devnull_in,
                         stdout=out, stderr=subprocess.STDOUT, start_new_session=True, cwd=STATE_DIR)
    deadline = time.monotonic() + wait_seconds
    while time.monotonic() < deadline:
        if ping() and port_in_use(port()):
            info(f"running (pid {pid()}) on {bind_address()}:{port()}; socket {SOCKET.relative_to(ROOT)}")
            return
        time.sleep(1)
    fail(f"server did not become ready in {wait_seconds}s; see {ERROR_LOG.relative_to(ROOT)}")


def stop(wait_seconds=60):
    process = pid()
    if not process:
        info("not running")
        return
    if ping():
        admin_client("-e", "SHUTDOWN")
    else:
        os.kill(process, signal.SIGTERM)
    deadline = time.monotonic() + wait_seconds
    while time.monotonic() < deadline:
        if not pid():
            info("stopped")
            return
        time.sleep(1)
    fail(f"pid {process} did not stop within {wait_seconds}s")


def status():
    process = pid()
    if process and ping():
        version = admin_client("-e", "SELECT VERSION()").stdout.strip()
        info(f"running MySQL {version} (pid {process}) on {bind_address()}:{port()}")
        return 0
    info("not running" if not process else f"pid {process} present but not answering")
    return 1


def read_env(path):
    if not path.is_file():
        fail(f"env file {path} not found; use --env-file, MYSQL_LOCAL_ENV_FILE or K8S_ENV_FILE.")
    values = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
            value = value[1:-1]
        values[key.strip().removeprefix("export ").strip()] = value
    return values


def check_inventory():
    text = COMPOSE_SQL.read_text()
    grants = set(re.findall(r"GRANT ALL PRIVILEGES ON (\w+)\.\* TO '(\w+)'@'%'", text))
    expected = {(schema, user) for _, schema, user in SERVICES}
    if grants != expected:
        fail(f"service inventory drifted from {COMPOSE_SQL.relative_to(ROOT)}: "
             f"{sorted(grants ^ expected)}")


def accounts(env_file):
    env = read_env(env_file)
    result, missing = [], []
    for prefix, default_schema, default_user in SERVICES:
        schema = env.get(f"{prefix}_DB_NAME") or default_schema
        user = env.get(f"{prefix}_DB_USER") or default_user
        password = env.get(f"{prefix}_DB_PASSWORD")
        if not password:
            missing.append(f"{prefix}_DB_PASSWORD")
            continue
        for identifier in (schema, user):
            if not IDENTIFIER.fullmatch(identifier):
                fail(f"invalid identifier for {prefix}: {identifier!r}")
        result.append((prefix, schema, user, password))
    if missing:
        fail(f"missing in {env_file}: {', '.join(missing)}")
    if len({user for _, _, user, _ in result}) != len(result):
        fail("each service must use a distinct database user.")
    if len({schema for _, schema, _, _ in result}) != len(result):
        fail("each service must use a distinct schema.")
    return result


def provision(env_file):
    check_inventory()
    service_accounts = accounts(env_file)
    if not ping():
        fail("server is not running; run scripts/local-mysql-start.sh first.")
    statements = ["SET SESSION sql_log_off = ON;"]
    for _, schema, user, password in service_accounts:
        account = f"'{user}'@'%'"
        statements += [
            f"CREATE DATABASE IF NOT EXISTS `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;",
            f"CREATE USER IF NOT EXISTS {account} IDENTIFIED BY {sql_string(password)};",
            f"ALTER USER {account} IDENTIFIED BY {sql_string(password)} PASSWORD EXPIRE NEVER ACCOUNT UNLOCK;",
            f"REVOKE ALL PRIVILEGES, GRANT OPTION FROM {account};",
            f"GRANT {SCHEMA_PRIVILEGES} ON `{schema}`.* TO {account};",
        ]
    admin_client(sql="\n".join(statements) + "\n")
    info(f"provisioned {len(service_accounts)} schemas/users from {env_file} (least-privilege, schema-scoped)")


def user_client(user, password, sql):
    handle = tempfile.NamedTemporaryFile("w", dir=TMP_DIR, prefix="verify-", suffix=".cnf", delete=False)
    path = pathlib.Path(handle.name)
    try:
        path.chmod(0o600)
        handle.write(f"[client]\nuser={user}\npassword={cnf_value(password)}\n")
        handle.close()
        return subprocess.run([binary("mysql"), f"--defaults-file={path}", "--protocol=TCP",
                               "--host=127.0.0.1", f"--port={port()}", "--batch", "--skip-column-names"],
                              input=sql, capture_output=True, text=True)
    finally:
        handle.close()
        path.unlink(missing_ok=True)


SYSTEM_USERS = {"root", "mysql.infoschema", "mysql.session", "mysql.sys"}
SYSTEM_SCHEMAS = {"information_schema", "mysql", "performance_schema", "sys"}


def verify_inventory(service_accounts):
    expected_users = {(user, "%") for _, _, user, _ in service_accounts}
    expected_schemas = {schema for _, schema, _, _ in service_accounts}
    rows = admin_client("-e", "SELECT user, host FROM mysql.user").stdout.splitlines()
    users = {tuple(row.split("\t")) for row in rows} - {(u, "localhost") for u in SYSTEM_USERS}
    schemas = set(admin_client("-e", "SHOW DATABASES").stdout.split()) - SYSTEM_SCHEMAS
    failures = 0
    if users != expected_users:
        info(f"FAIL user inventory: missing {sorted(expected_users - users)}, unexpected {sorted(users - expected_users)}")
        failures += 1
    if schemas != expected_schemas:
        info(f"FAIL schema inventory: missing {sorted(expected_schemas - schemas)}, unexpected {sorted(schemas - expected_schemas)}")
        failures += 1
    if not failures:
        info(f"inventory exact: {len(schemas)} schemas, {len(users)} service users, root@localhost socket-only")
    return failures


def verify(env_file):
    service_accounts = accounts(env_file)
    if not ping():
        fail("server is not running.")
    all_schemas = {schema for _, schema, _, _ in service_accounts}
    probe = "CREATE TABLE IF NOT EXISTS zz_local_mysql_probe (id INT PRIMARY KEY);\n" \
            "DROP TABLE zz_local_mysql_probe;\n"
    failures = 0
    for prefix, schema, user, password in service_accounts:
        result = user_client(user, password, f"USE `{schema}`;\n{probe}SHOW DATABASES;\n")
        visible = set(result.stdout.split()) - {"information_schema", "performance_schema"}
        if result.returncode != 0:
            info(f"FAIL {prefix:<13} {user}@{schema}: {result.stderr.strip()}")
            failures += 1
        elif visible != {schema}:
            info(f"FAIL {prefix:<13} {user} sees unexpected schemas {sorted(visible - {schema})}")
            failures += 1
        else:
            other = sorted(all_schemas - {schema})[0]
            denied = user_client(user, password, f"USE `{other}`;\n")
            if denied.returncode == 0:
                info(f"FAIL {prefix:<13} {user} can access {other}")
                failures += 1
            else:
                info(f"OK   {prefix:<13} {user} -> {schema} (DDL/DML ok, cross-schema denied)")
    failures += verify_inventory(service_accounts)
    if failures:
        fail(f"{failures} verification check(s) failed")
    info(f"verified {len(service_accounts)} isolated accounts on 127.0.0.1:{port()}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=USAGE,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=("start", "stop", "status", "provision", "verify", "init"))
    parser.add_argument("--env-file", default=os.environ.get("MYSQL_LOCAL_ENV_FILE")
                        or os.environ.get("K8S_ENV_FILE") or ".env.example",
                        help="dotenv file with *_DB_PASSWORD values "
                             "(default: $MYSQL_LOCAL_ENV_FILE, then $K8S_ENV_FILE, then .env.example)")
    args = parser.parse_args()
    os.umask(0o077)
    env_file = pathlib.Path(args.env_file)
    if not env_file.is_absolute():
        env_file = ROOT / env_file
    if args.command == "start":
        start()
    elif args.command == "stop":
        stop()
    elif args.command == "status":
        sys.exit(status())
    elif args.command == "provision":
        provision(env_file)
    elif args.command == "verify":
        verify(env_file)
    else:
        accounts(env_file)
        start()
        provision(env_file)
        verify(env_file)


if __name__ == "__main__":
    main()
