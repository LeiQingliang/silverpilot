#!/bin/sh
set -eu
umask 077

# This image has one fixed port. Railway's domain target and PORT must be 8080.
if [ "${PORT:-8080}" != "8080" ]; then
    printf '%s\n' 'Set the frontend PORT and public domain target port to 8080.' >&2
    exit 1
fi

backend_host=${SILVERPILOT_BACKEND_HOST:-backend.railway.internal}
if [ "${#backend_host}" -gt 253 ] || ! printf '%s\n' "$backend_host" | awk '
    BEGIN { valid = 1 }
    {
        count = split($0, labels, ".")
        for (i = 1; i <= count; i++) {
            if (length(labels[i]) < 1 || length(labels[i]) > 63 ||
                labels[i] !~ /^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?$/) valid = 0
        }
        if (NR != 1) valid = 0
    }
    END { exit !valid }
'; then
    printf '%s\n' 'SILVERPILOT_BACKEND_HOST must be a DNS hostname without a scheme, port or path.' >&2
    exit 1
fi

# Use Railway's runtime DNS rather than a public resolver, which cannot resolve
# railway.internal. Brackets preserve IPv6 nameserver addresses in Nginx syntax.
resolvers=
for nameserver in $(awk '$1 == "nameserver" { print $2 }' /etc/resolv.conf); do
    case "$nameserver" in
        *[!0-9a-fA-F:.]*|'')
            printf '%s\n' 'Invalid nameserver in /etc/resolv.conf.' >&2
            exit 1
            ;;
        *:*) nameserver="[$nameserver]" ;;
        *.*) ;;
        *)
            printf '%s\n' 'A nameserver must be an IPv4 or IPv6 address.' >&2
            exit 1
            ;;
    esac
    resolvers="${resolvers}${resolvers:+ }${nameserver}"
done
if [ -z "$resolvers" ]; then
    printf '%s\n' 'No runtime DNS nameserver found; refusing to start an unreachable proxy.' >&2
    exit 1
fi

# Values have restricted alphabets, so they cannot introduce Nginx directives
# or sed replacements. Substitute only these markers, preserving Nginx $vars.
sed -e "s|@@BACKEND_HOST@@|$backend_host|g" \
    -e "s|@@DNS_RESOLVERS@@|$resolvers|g" \
    /etc/nginx/nginx.conf.template > /tmp/silverpilot-nginx.conf

nginx -t -c /tmp/silverpilot-nginx.conf
exec nginx -c /tmp/silverpilot-nginx.conf -g 'daemon off;'
