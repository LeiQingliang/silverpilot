ARG REDIS_VERSION=8.2.9
ARG REDIS_DOWNLOAD_SHA=531b314e5557ad76d941f605b3e3162ac61dc141f37c407e1f91fcfe17ea8c30

FROM alpine:3.24.1@sha256:28bd5fe8b56d1bd048e5babf5b10710ebe0bae67db86916198a6eec434943f8b AS builder
ARG REDIS_VERSION
ARG REDIS_DOWNLOAD_SHA

RUN apk add --no-cache build-base linux-headers openssl-dev tar wget
RUN wget -O /tmp/redis.tar.gz "https://download.redis.io/releases/redis-${REDIS_VERSION}.tar.gz" \
    && echo "${REDIS_DOWNLOAD_SHA}  /tmp/redis.tar.gz" | sha256sum -c - \
    && mkdir -p /usr/src/redis \
    && tar -xzf /tmp/redis.tar.gz -C /usr/src/redis --strip-components=1 \
    && make -C /usr/src/redis -j"$(getconf _NPROCESSORS_ONLN)" BUILD_TLS=yes MALLOC=libc \
    && make -C /usr/src/redis install PREFIX=/opt/redis BUILD_TLS=yes MALLOC=libc

FROM alpine:3.24.1@sha256:28bd5fe8b56d1bd048e5babf5b10710ebe0bae67db86916198a6eec434943f8b AS runtime
ARG REDIS_VERSION
ARG REDIS_DOWNLOAD_SHA

RUN apk upgrade --no-cache \
    && apk add --no-cache ca-certificates libcrypto3 libssl3 libstdc++ su-exec tzdata \
    && addgroup -S -g 1000 redis \
    && adduser -S -D -H -u 999 -G redis redis \
    && mkdir -p /data \
    && chown redis:redis /data
COPY --from=builder /opt/redis/bin/ /usr/local/bin/
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod 0755 /usr/local/bin/docker-entrypoint.sh

LABEL org.opencontainers.image.title="SilverPilot Redis" \
      org.opencontainers.image.description="Redis 8.2 Extended built from the checksum-verified official source release" \
      org.opencontainers.image.source="https://github.com/redis/redis" \
      org.opencontainers.image.version="${REDIS_VERSION}"

ENV REDIS_VERSION=${REDIS_VERSION} \
    REDIS_DOWNLOAD_SHA=${REDIS_DOWNLOAD_SHA}
WORKDIR /data
VOLUME ["/data"]
EXPOSE 6379
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["redis-server"]
