# Build from the repository root. Keep the baseline aligned with database/Dockerfile.
FROM golang:1.27.0-alpine3.24@sha256:4c9fe60190a2a3350ddc51de80d0224b8a6698d12bdfc999fee45ea9d6c46dbc AS gosu-builder
ARG GOSU_VERSION=1.19
RUN CGO_ENABLED=0 go install github.com/tianon/gosu@${GOSU_VERSION}

FROM mysql:9.7.2@sha256:257388edf9c84dbc04c763625446d5f3fa6ed60d1b0873bc552c614ba0a7ab4e AS prepared
RUN microdnf upgrade -y curl-7.76.1-40.el9_8.5 libcurl-7.76.1-40.el9_8.5 \
    && microdnf remove -y mysql-shell \
    && microdnf install -y httpd-tools \
    && microdnf clean all \
    && rm -rf /var/cache/dnf /usr/local/bin/gosu
COPY --from=gosu-builder /go/bin/gosu /usr/local/bin/gosu
COPY database/a_old.sql /opt/silverpilot/a_old.sql
COPY deploy/railway/mysql-init-accounts.sh /docker-entrypoint-initdb.d/10-silverpilot.sh
COPY deploy/railway/mysql-entrypoint.sh /usr/local/bin/silverpilot-mysql-entrypoint.sh
# The official MySQL entrypoint sources non-executable .sh files, which makes
# its docker_process_sql helper available without exposing database credentials.
RUN chmod 0644 /docker-entrypoint-initdb.d/10-silverpilot.sh \
    && chmod 0755 /usr/local/bin/silverpilot-mysql-entrypoint.sh

FROM scratch
COPY --from=prepared / /
ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    GOSU_VERSION=1.19 \
    MYSQL_MAJOR=9.7 \
    MYSQL_VERSION=9.7.2-1.el9 \
    MYSQL_DATABASE=a_old \
    MYSQL_USER=cecsms \
    TZ=Asia/Shanghai
# Railway mounts the persistent volume at /var/lib/mysql through service settings.
# Its builder rejects Dockerfile VOLUME directives.
EXPOSE 3306
ENTRYPOINT ["silverpilot-mysql-entrypoint.sh"]
CMD ["mysqld", "--bind-address=::", "--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci", "--default-time-zone=+08:00"]
