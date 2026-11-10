#!/bin/sh
set -eu

# Keep the official image's option-only invocation convention.
if [ "$#" -gt 0 ] && [ "$1" != "${1#-}" ]; then
    set -- mysqld "$@"
fi

# This image initializes a new, dedicated Railway volume. An interrupted import
# must never be mistaken for a complete database on an automatic restart.
if [ "${1:-}" = "mysqld" ]; then
    if [ -n "${RAILWAY_ENVIRONMENT_ID:-}" ] && [ "${RAILWAY_VOLUME_MOUNT_PATH:-}" != '/var/lib/mysql' ]; then
        echo 'Attach the MySQL volume at /var/lib/mysql before deployment.' >&2
        exit 1
    fi
    if [ -d /var/lib/mysql/mysql ]; then
        if [ ! -f /var/lib/mysql/.silverpilot-railway-initialized-v1 ]; then
            echo 'SilverPilot database initialization is incomplete or this is an unrelated volume. Refusing to start; inspect the volume before recovery.' >&2
            exit 1
        fi
        # Application account passwords are bootstrap inputs, not mysqld inputs.
        unset SILVERPILOT_ADMIN_PASSWORD SILVERPILOT_VISITOR_PASSWORD
    else
        # Check configuration before the official entrypoint creates any data.
        sh /docker-entrypoint-initdb.d/10-silverpilot.sh --validate-only
    fi
fi

exec docker-entrypoint.sh "$@"
