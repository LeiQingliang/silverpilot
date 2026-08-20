#!/bin/sh
set -eu

if [ "$(id -u)" = "0" ]; then
    chown -R redis:redis /data
    exec su-exec redis:redis "$@"
fi

exec "$@"
