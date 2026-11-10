#!/bin/sh
set -eu

# Railway's healthcheck and the private frontend upstream must agree on 8083.
if [ "${PORT:-8083}" != "8083" ] || [ "${CECSMS_SERVER_PORT:-8083}" != "8083" ]; then
    echo 'SilverPilot backend requires PORT=8083 and CECSMS_SERVER_PORT=8083.' >&2
    exit 1
fi
if [ "${CECSMS_UPLOAD_DIR:-/app/uploads}" != "/app/uploads" ]; then
    echo 'SilverPilot backend requires CECSMS_UPLOAD_DIR=/app/uploads.' >&2
    exit 1
fi
if [ -n "${RAILWAY_ENVIRONMENT_ID:-}" ] && [ "${RAILWAY_VOLUME_MOUNT_PATH:-}" != "/app/uploads" ]; then
    echo 'Attach a Railway volume to this backend at /app/uploads before starting.' >&2
    exit 1
fi
if [ "$#" -ne 0 ]; then
    echo 'Do not override this image entrypoint or provide a custom start command.' >&2
    exit 1
fi

# Do not recursively chown a persistent volume. New files are already created
# by UID 10001; only the root-owned mount point needs adjustment at startup.
if [ "$(id -u)" = "0" ]; then
    if [ -L /app/uploads ] || [ -L /app/logs ]; then
        echo 'Upload and log directories must not be symbolic links.' >&2
        exit 1
    fi
    mkdir -p /app/uploads /app/logs
    chown 10001:10001 /app/uploads /app/logs
elif [ "$(id -u)" != "10001" ]; then
    echo 'Start this image as root for volume setup, or as the prepared UID 10001.' >&2
    exit 1
fi

# An IPv6 wildcard with the JVM dual-stack socket supports Railway's private
# IPv6 DNS as well as IPv4 healthchecks on Linux. Never force IPv4-only mode.
set -- java \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/urandom \
    -Djava.net.preferIPv4Stack=false \
    -jar /app/cecsms-serve.jar \
    --server.address=:: \
    --server.port=8083 \
    --file.image-base-path=/app/uploads

if [ "$(id -u)" = "0" ]; then
    exec setpriv --reuid=10001 --regid=10001 --init-groups \
        --no-new-privs --inh-caps=-all --ambient-caps=-all --bounding-set=-all "$@"
fi
exec "$@"
