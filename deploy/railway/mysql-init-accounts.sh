#!/bin/sh
# Sourced by the official MySQL entrypoint during FIRST initialization only.
# Do not enable shell tracing: runtime variables contain bootstrap credentials.
set -eu

sp_fail() {
    echo "SilverPilot database bootstrap: $1" >&2
    exit 1
}

sp_validate_secret() {
    # Use 32-72 random base64url/hex characters; bcrypt has a 72-byte limit.
    [ "${#2}" -ge 32 ] && [ "${#2}" -le 72 ] \
        || sp_fail "$1 must contain 32-72 random ASCII characters."
    case "$2" in
        *[!A-Za-z0-9_-]*) sp_fail "$1 must use only letters, digits, underscore or hyphen." ;;
    esac
}

sp_validate() {
    [ "${MYSQL_DATABASE:-}" = "a_old" ] || sp_fail 'MYSQL_DATABASE must be a_old.'
    [ "${MYSQL_USER:-}" = "cecsms" ] || sp_fail 'MYSQL_USER must be cecsms.'
    [ -z "${MYSQL_ALLOW_EMPTY_PASSWORD:-}" ] || sp_fail 'Empty root passwords are not allowed.'
    [ -z "${MYSQL_RANDOM_ROOT_PASSWORD:-}" ] || sp_fail 'Provide MYSQL_ROOT_PASSWORD explicitly.'
    [ -z "${MYSQL_ONETIME_PASSWORD:-}" ] || sp_fail 'MYSQL_ONETIME_PASSWORD is not supported.'
    sp_validate_secret MYSQL_ROOT_PASSWORD "${MYSQL_ROOT_PASSWORD:-}"
    sp_validate_secret MYSQL_PASSWORD "${MYSQL_PASSWORD:-}"
    sp_validate_secret SILVERPILOT_ADMIN_PASSWORD "${SILVERPILOT_ADMIN_PASSWORD:-}"
    sp_validate_secret SILVERPILOT_VISITOR_PASSWORD "${SILVERPILOT_VISITOR_PASSWORD:-}"
    [ "$SILVERPILOT_ADMIN_PASSWORD" != "$SILVERPILOT_VISITOR_PASSWORD" ] \
        || sp_fail 'Administrator and visitor passwords must differ.'
    [ "$MYSQL_ROOT_PASSWORD" != "$MYSQL_PASSWORD" ] \
        || sp_fail 'Database root and application passwords must differ.'
    [ "$SILVERPILOT_ADMIN_PASSWORD" != "$MYSQL_ROOT_PASSWORD" ] \
        && [ "$SILVERPILOT_ADMIN_PASSWORD" != "$MYSQL_PASSWORD" ] \
        && [ "$SILVERPILOT_VISITOR_PASSWORD" != "$MYSQL_ROOT_PASSWORD" ] \
        && [ "$SILVERPILOT_VISITOR_PASSWORD" != "$MYSQL_PASSWORD" ] \
        || sp_fail 'Every bootstrap credential must be different.'
}

sp_validate
if [ "${1:-}" = "--validate-only" ]; then
    exit 0
fi

sp_hash() {
    # -i reads stdin; -n returns a hash without a password file. Passwords are
    # never command arguments, SQL literals, build arguments or log messages.
    sp_htpasswd=$(printf '%s\n' "$1" | htpasswd -niBC 12 silverpilot 2>/dev/null) \
        || sp_fail 'Could not hash a bootstrap password.'
    sp_hash_value=${sp_htpasswd#silverpilot:}
    printf '%s\n' "$sp_hash_value" | grep -Eq '^\$2[aby]\$12\$[./A-Za-z0-9]{53}$' \
        || sp_fail 'Unexpected bcrypt output.'
    printf '%s' "$sp_hash_value"
}

sp_admin_hash=$(sp_hash "$SILVERPILOT_ADMIN_PASSWORD")
sp_visitor_hash=$(sp_hash "$SILVERPILOT_VISITOR_PASSWORD")
unset SILVERPILOT_ADMIN_PASSWORD SILVERPILOT_VISITOR_PASSWORD

# Sanitize BEFORE importing. A failed partial import can contain only NULL
# application passwords, which UserServiceImpl.passwordMatches rejects.
# Fail on a changed fixture format instead of silently importing a default hash.
sp_seed=/opt/silverpilot/a_old.sql
sp_sanitized=$(mktemp /tmp/silverpilot-seed.XXXXXX)
sed -E "/^INSERT INTO \`user\` VALUES /s/^(INSERT INTO \`user\` VALUES \([0-9]+, [0-9]+, '[A-Za-z0-9_]+', )'[^']*',/\1NULL,/" \
    "$sp_seed" > "$sp_sanitized"
sp_original_count=$(grep -c '^INSERT INTO `user` ' "$sp_seed" || true)
sp_locked_count=$(grep -Ec "^INSERT INTO \`user\` VALUES \([0-9]+, [0-9]+, '[A-Za-z0-9_]+', NULL," "$sp_sanitized" || true)
if [ "$sp_original_count" != 18 ] || [ "$sp_locked_count" != "$sp_original_count" ]; then
    rm -f "$sp_sanitized"
    sp_fail 'Seed account format/count changed; review the sanitizer before deployment.'
fi

if ! docker_process_sql --database=a_old < "$sp_sanitized" >/dev/null 2>&1; then
    rm -f "$sp_sanitized"
    sp_fail 'Synthetic database import failed. No completion marker was written.'
fi
rm -f "$sp_sanitized"

sp_account_check=$(docker_process_sql --database=a_old --batch --skip-column-names <<'SQL'
SELECT CONCAT(COUNT(*), ':', SUM(`password` IS NULL), ':',
              SUM(`id` = 1 AND `username` = 'admin' AND `roleId` = 1), ':',
              SUM(`id` = 17 AND `username` = 'linge' AND `roleId` = 4))
FROM `user`;
SQL
)
[ "$sp_account_check" = '18:18:1:1' ] || sp_fail 'Unexpected seed identities or unlocked accounts.'

if ! docker_process_sql --database=a_old >/dev/null 2>&1 <<SQL
START TRANSACTION;
UPDATE \`user\` SET \`password\` = '$sp_admin_hash'
WHERE \`id\` = 1 AND \`username\` = 'admin' AND \`roleId\` = 1;
UPDATE \`user\` SET \`password\` = '$sp_visitor_hash'
WHERE \`id\` = 17 AND \`username\` = 'linge' AND \`roleId\` = 4;
COMMIT;
SQL
then
    sp_fail 'Could not enable bootstrap accounts. No completion marker was written.'
fi

sp_enabled_count=$(docker_process_sql --database=a_old --batch --skip-column-names <<'SQL'
SELECT COUNT(*) FROM `user` WHERE `password` IS NOT NULL;
SQL
)
[ "$sp_enabled_count" = 2 ] || sp_fail 'Unexpected number of enabled seed accounts.'
touch /var/lib/mysql/.silverpilot-railway-initialized-v1
unset sp_admin_hash sp_visitor_hash sp_htpasswd sp_hash_value
echo 'SilverPilot synthetic database initialized; two bootstrap accounts enabled and all other seed accounts locked.'
