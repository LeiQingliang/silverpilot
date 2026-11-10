#!/bin/sh
set -eu

REDIS_PASSWORD=${REDIS_PASSWORD:-}
if [ "${#REDIS_PASSWORD}" -lt 32 ]; then
    echo 'REDIS_PASSWORD must contain at least 32 random characters.' >&2
    exit 1
fi
if [ -n "${RAILWAY_ENVIRONMENT_ID:-}" ] && [ "${RAILWAY_VOLUME_MOUNT_PATH:-}" != '/data' ]; then
    echo 'Attach the Redis volume at /data before deployment.' >&2
    exit 1
fi
exec /usr/local/bin/docker-entrypoint.sh redis-server \
    --bind '::' '0.0.0.0' \
    --protected-mode yes \
    --appendonly yes \
    --appendfsync everysec \
    --requirepass "$REDIS_PASSWORD"
