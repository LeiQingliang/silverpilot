FROM golang:1.27.0-alpine3.24@sha256:4c9fe60190a2a3350ddc51de80d0224b8a6698d12bdfc999fee45ea9d6c46dbc AS gosu-builder
ARG GOSU_VERSION=1.19
RUN CGO_ENABLED=0 go install github.com/tianon/gosu@${GOSU_VERSION}

FROM mysql:9.7.2@sha256:257388edf9c84dbc04c763625446d5f3fa6ed60d1b0873bc552c614ba0a7ab4e AS prepared

# Upgrade the curl runtime from the supported Oracle Linux repository before
# flattening the image so newly fixed transport CVEs do not remain in the
# otherwise digest-pinned upstream snapshot.
# mysql-shell embeds an additional Python/Go runtime that is not used by the
# server, initialization entrypoint, healthcheck, or this application's SQL
# workflow. Removing it cuts the runtime attack surface while retaining
# mysqld, mysql, mysqladmin and mysqldump from server-minimal.
RUN microdnf upgrade -y curl-7.76.1-40.el9_8.5 libcurl-7.76.1-40.el9_8.5 \
    && microdnf remove -y mysql-shell \
    && microdnf clean all \
    && rm -rf /var/cache/dnf /usr/local/bin/gosu
COPY --from=gosu-builder /go/bin/gosu /usr/local/bin/gosu

# Copy the prepared root filesystem into a fresh final layer. A plain package
# removal leaves mysql-shell bytes in an inherited image layer, so scanners and
# anyone with layer access can still see that unnecessary runtime. The final
# stage preserves the official entrypoint/server contract without those bytes.
FROM scratch
COPY --from=prepared / /

ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    GOSU_VERSION=1.19 \
    MYSQL_MAJOR=9.7 \
    MYSQL_VERSION=9.7.2-1.el9
VOLUME ["/var/lib/mysql"]
EXPOSE 3306 33060
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["mysqld"]
