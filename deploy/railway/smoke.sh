#!/usr/bin/env bash
# Linux/Docker integration test. All accounts, containers and volumes are
# ephemeral. Never run with shell tracing or reuse an existing Compose project.
set +x
set -Eeuo pipefail
umask 077

for required in docker curl openssl python3; do
    command -v "$required" >/dev/null || { echo "Missing required tool: $required" >&2; exit 1; }
done
docker compose version >/dev/null
docker info >/dev/null 2>&1 || { echo 'Docker is not available.' >&2; exit 1; }

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
cd "$repo_root"
smoke_tmp=$(mktemp -d /tmp/silverpilot-railway-smoke.XXXXXXXX)
smoke_project="sp-railway-smoke-$(openssl rand -hex 6)"
smoke_env="$smoke_tmp/runtime.env"
export COMPOSE_PARALLEL_LIMIT=2

for key in MYSQL_ROOT_PASSWORD MYSQL_PASSWORD SILVERPILOT_ADMIN_PASSWORD SILVERPILOT_VISITOR_PASSWORD REDIS_PASSWORD CECSMS_JWT_SECRET CECSMS_MCP_API_KEY REGISTRATION_PASSWORD; do
    value=$(openssl rand -hex 32)
    printf -v "$key" '%s' "$value"
    export "$key"
    printf '%s=%s\n' "$key" "$value" >> "$smoke_env"
done
unset value

dc() {
    docker compose --project-name "$smoke_project" --env-file "$smoke_env" \
        -f "$repo_root/deploy/railway/compose.smoke.yaml" "$@"
}

# Collect diagnostic files first, then sanitize exact secrets, hashes, JDBC
# URLs and query strings. Never print docker inspect/config or raw SQL output.
diagnostics() {
    echo 'Isolated deployment failed. Sanitized diagnostic tail:' >&2
    dc ps --all --format 'table {{.Service}}\t{{.State}}\t{{.Health}}\t{{.ExitCode}}' > "$smoke_tmp/status.log" 2>&1 || true
    dc logs --no-color --tail 35 > "$smoke_tmp/services.log" 2>&1 || true
    python3 - "$smoke_tmp" <<'PY'
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
secrets = [line.partition('=')[2] for line in (root / 'runtime.env').read_text().splitlines()]
for name in ('build.log', 'operations.log', 'status.log', 'services.log', 'checks.log'):
    path = root / name
    if not path.exists():
        continue
    content = '\n'.join(path.read_text(errors='replace').splitlines()[-45:])
    for secret in secrets:
        if secret:
            content = content.replace(secret, '[secret]')
    content = re.sub(r'\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}', '[bcrypt]', content)
    content = re.sub(r'jdbc:mysql://[^\s\"\']+', '[jdbc-url]', content)
    content = re.sub(r'\?[^\s\"\']+', '?[query-redacted]', content)
    print(f'--- {name} ---', file=sys.stderr)
    print(content, file=sys.stderr)
PY
}

cleanup() {
    result=$?
    trap - EXIT
    set +e
    if [ "$result" -ne 0 ]; then diagnostics; fi
    # The generated project name cannot refer to a user project. Never prune
    # Docker globally or remove a volume outside this Compose project.
    if [[ "$smoke_project" =~ ^sp-railway-smoke-[0-9a-f]{12}$ ]]; then
        dc down --volumes --remove-orphans --timeout 30 > "$smoke_tmp/cleanup.log" 2>&1
        if [ "$?" -ne 0 ]; then
            echo "Could not clean up isolated project $smoke_project." >&2
            result=1
        fi
    fi
    if [[ "$smoke_tmp" == /tmp/silverpilot-railway-smoke.* && -d "$smoke_tmp" ]]; then
        rm -rf -- "$smoke_tmp"
    fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

sql() {
    dc exec -T mysql sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --protocol=TCP -h127.0.0.1 -uroot --database=a_old --batch --skip-column-names' \
        2>> "$smoke_tmp/operations.log"
}

expect_seed_accounts() {
    actual=$(sql <<'SQL'
SELECT CONCAT(COUNT(*), ':', SUM(`password` IS NULL), ':',
SUM(`username` = 'admin' AND `id` = 1 AND `roleId` = 1 AND `password` REGEXP '^[$]2[aby][$]12[$]'), ':',
SUM(`username` = 'linge' AND `id` = 17 AND `roleId` = 4 AND `password` REGEXP '^[$]2[aby][$]12[$]'))
FROM `user` WHERE `id` BETWEEN 1 AND 18;
SQL
)
    [ "$actual" = '18:16:1:1' ] || { echo 'Seed account lock/hash contract failed.' >&2; return 1; }
}

echo 'Building the four Railway deployment images.'
dc config --quiet
dc build > "$smoke_tmp/build.log" 2>&1
# Run all bounded burst/concurrency checks against a stub using the exact
# Nginx binary from the new image. No database, model, credentials or public
# address is involved. Each test owns and removes its temporary container.
edge_image=$(docker image inspect "$(dc images --quiet frontend)" --format '{{.Id}}' 2>/dev/null || true)
if [ -z "$edge_image" ]; then
    # Compose does not list images until containers exist on some versions.
    edge_image=$(docker image inspect "${smoke_project}-frontend" --format '{{.Id}}')
fi
python3 deploy/railway/edge-smoke.py --docker-image "$edge_image"
echo 'Starting the isolated stack and checking dependency health.'
dc up --detach --no-build --wait --wait-timeout 300 >> "$smoke_tmp/operations.log" 2>&1
expect_seed_accounts
initial_user_count=$(printf 'SELECT COUNT(*) FROM `user`;\n' | sql)
[ "$initial_user_count" = 18 ]

base_url=http://127.0.0.1:8082
curl --fail --silent --show-error "$base_url/login" -o "$smoke_tmp/login.html"
curl --fail --silent --show-error "$base_url/readyz" -o "$smoke_tmp/health.json"
python3 - "$smoke_tmp" <<'PY'
from pathlib import Path
import json
import sys
root = Path(sys.argv[1])
assert '<html' in (root / 'login.html').read_text().lower(), 'Login page is missing'
assert json.loads((root / 'health.json').read_text())['status'] == 'UP', 'Backend health is not UP'
PY

# Check an actual bundled image, not an SPA fallback with a 200 response.
image_path=$(find image -type f -name '*.png' -print -quit)
[ -n "$image_path" ]
curl --fail --silent --show-error "$base_url/$image_path" -o "$smoke_tmp/image.png"
cmp --silent "$image_path" "$smoke_tmp/image.png"

# Only checks rejections and anonymous health at a literal loopback address.
# Burst/concurrency tests use a standalone stub, never this backend or an LLM.
python3 deploy/railway/edge-smoke.py --base-url "$base_url"

# Verify the deployed JAR's actual passwordMatches implementation against both
# bootstrap hashes. A standalone JVM test does not issue or bypass a captcha.
mkdir "$smoke_tmp/password-check"
sql <<'SQL' > "$smoke_tmp/password-check/hashes.tsv"
SELECT `username`, `password` FROM `user` WHERE `username` IN ('admin', 'linge') ORDER BY `username`;
SQL
printf '%s\n%s\n' "$SILVERPILOT_ADMIN_PASSWORD" "$SILVERPILOT_VISITOR_PASSWORD" > "$smoke_tmp/password-check/passwords.txt"
dc cp backend:/app/cecsms-serve.jar "$smoke_tmp/backend.jar" >> "$smoke_tmp/operations.log" 2>&1
python3 - "$smoke_tmp" <<'PY'
from pathlib import Path
from zipfile import ZipFile
import sys
root = Path(sys.argv[1])
target = root / 'password-check'
with ZipFile(root / 'backend.jar') as archive:
    for entry in archive.infolist():
        if entry.is_dir():
            continue
        if entry.filename.startswith('BOOT-INF/lib/'):
            destination = target / 'lib' / Path(entry.filename).name
        elif entry.filename.startswith('BOOT-INF/classes/'):
            relative = Path(entry.filename.removeprefix('BOOT-INF/classes/'))
            if '..' in relative.parts or relative.is_absolute():
                raise ValueError('Unsafe class archive path')
            destination = target / 'classes' / relative
        else:
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(archive.read(entry))
PY
cat > "$smoke_tmp/password-check/PasswordCompatibility.java" <<'JAVA'
import java.nio.file.Files;
import java.nio.file.Path;

class PasswordCompatibility {
    public static void main(String[] args) throws Exception {
        var root = Path.of("/checks");
        var passwords = Files.readAllLines(root.resolve("passwords.txt"));
        var hashes = Files.readAllLines(root.resolve("hashes.tsv"));
        if (passwords.size() != 2 || hashes.size() != 2) throw new AssertionError("Expected two accounts");
        var encoderType = Class.forName("org.springframework.security.crypto.password.PasswordEncoder");
        var encoder = Class.forName("org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder")
                .getConstructor(int.class).newInstance(12);
        var serviceType = Class.forName("com.cecsmsserve.service.impl.UserServiceImpl");
        var service = serviceType.getConstructor(Class.forName("com.cecsmsserve.mapper.UserMapper"),
                Class.forName("com.cecsmsserve.util.JWTUtil"), encoderType).newInstance(null, null, encoder);
        var matches = serviceType.getDeclaredMethod("passwordMatches", String.class, String.class);
        matches.setAccessible(true);
        for (int i = 0; i < 2; i++) {
            var parts = hashes.get(i).split("\t", -1);
            if (parts.length != 2 || !parts[0].equals(i == 0 ? "admin" : "linge")) {
                throw new AssertionError("Unexpected account identity");
            }
            if (!(Boolean) matches.invoke(service, passwords.get(i), parts[1])
                    || (Boolean) matches.invoke(service, "123456", parts[1])
                    || (Boolean) matches.invoke(service, passwords.get(1 - i), parts[1])
                    || (Boolean) matches.invoke(service, passwords.get(i), null)) {
                throw new AssertionError("Bootstrap password compatibility failed");
            }
        }
        System.out.println("PASS: deployed backend accepts both new hashes and rejects default, swapped and locked passwords.");
    }
}
JAVA
jdk_image=$(awk '$1 == "FROM" && $2 ~ /-jdk-/ { print $2; exit }' deploy/railway/backend.Dockerfile)
[[ "$jdk_image" == eclipse-temurin:*@sha256:* ]]
docker run --rm --network none --user "$(id -u):$(id -g)" \
    --mount "type=bind,source=$smoke_tmp/password-check,target=/checks,readonly" \
    --entrypoint java "$jdk_image" -cp '/checks/classes:/checks/lib/*' \
    /checks/PasswordCompatibility.java > "$smoke_tmp/checks.log" 2>&1
echo 'Bootstrap passwords are compatible with the deployed backend; default passwords are rejected.'

# Exercise the real public registration boundary without authenticating as an
# administrator. Request/response payloads remain in the temporary directory.
# Exactly two PUTs fit the production edge's registration burst: first missing
# CAPTCHA, then valid CAPTCHA. Replay is checked via consumed Redis state below,
# not a third HTTP request which could be rejected by the edge instead.
export SMOKE_REGISTRATION_NAME="ci_$(openssl rand -hex 8)"
python3 - "$smoke_tmp/registration.json" <<'PY'
import json
import os
from pathlib import Path
import sys
Path(sys.argv[1]).write_text(json.dumps({
    'id': 1, 'roleId': 1, 'username': os.environ['SMOKE_REGISTRATION_NAME'],
    'password': os.environ['REGISTRATION_PASSWORD'], 'name': 'Synthetic CI visitor',
    # Synthetic format-only fixtures; 990000 is a fictitious identity region.
    'telephone': ''.join(('139', '0000', '0000')),
    'idNum': ''.join(('990000', '19900101', '001', '0')),
    'role': 1, 'department': 'admin', 'point': 1000000, 'token': 'client-supplied-token'
}))
PY
registration_http=$(curl --silent --show-error -X PUT "$base_url/api/user/register" \
    -H 'Content-Type: application/json' -H "Origin: $base_url" \
    --data-binary "@$smoke_tmp/registration.json" -o "$smoke_tmp/registration-missing-captcha.json" \
    --write-out '%{http_code}')
python3 - "$smoke_tmp/registration-missing-captcha.json" "$registration_http" <<'PY'
import json
from pathlib import Path
import sys
assert sys.argv[2] in ('200', '400'), 'Registration rejection did not reach the expected application boundary'
result = json.loads(Path(sys.argv[1]).read_text())
assert result['code'] == 400 and result.get('result') is None, 'Missing CAPTCHA accepted an account'
PY
[ "$(printf 'SELECT COUNT(*) FROM `user`;\n' | sql)" = "$initial_user_count" ]

# Seed a one-time fixture only through this freshly created Compose Redis.
# No production API, application setting, or CAPTCHA-bypass endpoint exists.
python3 - "$smoke_tmp" <<'PY'
import hashlib
import json
from pathlib import Path
import sys
import uuid
root = Path(sys.argv[1])
challenge = 'ci-register-' + uuid.uuid4().hex
storage_key = 'cecsms:security:captcha:' + hashlib.sha256(challenge.encode()).hexdigest()[:32]
(root / 'registration-captcha-key.txt').write_text(storage_key)
path = root / 'registration.json'
payload = json.loads(path.read_text())
payload.update(captchaId=challenge, verificationCode='AB7Z')
path.write_text(json.dumps(payload))
PY
registration_captcha_key=$(cat "$smoke_tmp/registration-captcha-key.txt")
dc exec -T redis sh -c \
    'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw SET "$1" "$2" EX 300' \
    sh "$registration_captcha_key" AB7Z > "$smoke_tmp/registration-captcha-set.txt"
grep -qx OK "$smoke_tmp/registration-captcha-set.txt"
curl --fail --silent --show-error -X PUT "$base_url/api/user/register" \
    -H 'Content-Type: application/json' -H "Origin: $base_url" \
    --data-binary "@$smoke_tmp/registration.json" -o "$smoke_tmp/registration-result.json"
python3 - "$smoke_tmp/registration-result.json" <<'PY'
import json
from pathlib import Path
import sys
result = json.loads(Path(sys.argv[1]).read_text())
assert result['code'] == 200, 'Registration did not succeed'
user = result['result']
assert user['id'] > 18 and user['roleId'] == 4, 'Registration escalated identity or role'
assert user['point'] == 0 and user.get('department') is None, 'Registration retained staff fields or points'
assert user.get('token') is None and 'password' not in user, 'Registration exposed or accepted credentials'
PY
registration_count=$(printf "SELECT COUNT(*) FROM \`user\` WHERE username='%s' AND roleId=4 AND point=0 AND department IS NULL AND id>18;\n" "$SMOKE_REGISTRATION_NAME" | sql)
[ "$registration_count" = 1 ]
dc exec -T redis sh -c \
    'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw EXISTS "$1"' \
    sh "$registration_captcha_key" > "$smoke_tmp/registration-captcha-exists.txt"
grep -qx 0 "$smoke_tmp/registration-captcha-exists.txt"
expect_seed_accounts

# Check the Java process and mount using the application's real UID.
dc exec -T backend sh -c 'grep -Eq "^Uid:[[:space:]]+10001[[:space:]]" /proc/1/status' >> "$smoke_tmp/operations.log" 2>&1
dc exec -T --user 10001:10001 backend sh -c \
    'test ! -w /app/cecsms-serve.jar && test ! -w /app/knowledge-base && test ! -w /app/repository-media && printf "isolated-upload-probe\n" > /app/uploads/.ci-persistence-probe' \
    >> "$smoke_tmp/operations.log" 2>&1
dc exec -T redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw set silverpilot:ci:persistence alive' > "$smoke_tmp/redis-set.txt"
grep -qx OK "$smoke_tmp/redis-set.txt"

# Stop and recreate containers (same isolated volumes) to verify persistence
# and confirm bootstrap accounts are not reset on an ordinary redeployment.
echo 'Recreating containers to verify database, Redis and upload persistence.'
dc down --timeout 30 >> "$smoke_tmp/operations.log" 2>&1
dc up --detach --no-build --wait --wait-timeout 300 >> "$smoke_tmp/operations.log" 2>&1
expect_seed_accounts
sql <<'SQL' > "$smoke_tmp/restarted-hashes.tsv"
SELECT `username`, `password` FROM `user` WHERE `username` IN ('admin', 'linge') ORDER BY `username`;
SQL
cmp --silent "$smoke_tmp/password-check/hashes.tsv" "$smoke_tmp/restarted-hashes.tsv"
registration_count=$(printf "SELECT COUNT(*) FROM \`user\` WHERE username='%s' AND roleId=4 AND point=0 AND department IS NULL AND id>18;\n" "$SMOKE_REGISTRATION_NAME" | sql)
[ "$registration_count" = 1 ]
dc exec -T --user 10001:10001 backend sh -c 'grep -qx isolated-upload-probe /app/uploads/.ci-persistence-probe' >> "$smoke_tmp/operations.log" 2>&1
dc exec -T redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --raw get silverpilot:ci:persistence' > "$smoke_tmp/redis-get.txt"
grep -qx alive "$smoke_tmp/redis-get.txt"
curl --fail --silent --show-error "$base_url/readyz" -o "$smoke_tmp/restarted-health.json"
python3 - "$smoke_tmp/restarted-health.json" <<'PY'
import json
from pathlib import Path
import sys
assert json.loads(Path(sys.argv[1]).read_text())['status'] == 'UP'
PY
echo 'PASS: four images, healthy API, login page, bundled image, account bootstrap, registration authorization and persistent volumes.'
