# syntax=docker/dockerfile:1.7
# Build from the repository root. Keep the reviewed backend baseline in sync
# with SourceCode/cecsmsServe-springboot/Dockerfile.
FROM eclipse-temurin:25.0.3_9-jdk-resolute@sha256:76b2c606540ac8de355f28f5a3979071456a13a531989d93ace4a2d995785ff1 AS build
WORKDIR /workspace
COPY SourceCode/cecsmsServe-springboot/.mvn .mvn
COPY SourceCode/cecsmsServe-springboot/mvnw SourceCode/cecsmsServe-springboot/mvnw.cmd SourceCode/cecsmsServe-springboot/pom.xml ./
COPY SourceCode/cecsmsServe-springboot/src src
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    chmod +x mvnw \
    && attempt=1 \
    && while [ "$attempt" -le 3 ]; do \
         if ./mvnw -B -ntp -DskipTests package; then break; fi; \
         if [ "$attempt" -eq 3 ]; then exit 1; fi; \
         echo "Maven build request failed; bounded retry $attempt of 2"; \
         sleep $((attempt * 5)); \
         attempt=$((attempt + 1)); \
       done

FROM eclipse-temurin:25.0.3_9-jre-resolute@sha256:9492fc3850879f8b2a29a3eaf2005f591698f0f6c0427d812e342df8adde95aa AS runtime-rootfs
RUN rm -f /usr/bin/pebble \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl util-linux \
    && command -v setpriv \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 silverpilot \
    && useradd --system --uid 10001 --gid silverpilot --home-dir /app silverpilot
WORKDIR /app
COPY --from=build /workspace/target/cecsms-serve-*.jar /app/cecsms-serve.jar
COPY knowledge-base/ima-ready /app/knowledge-base
COPY image /app/repository-media/image
COPY file /app/repository-media/file
COPY video /app/repository-media/video
COPY deploy/railway/backend-entrypoint.sh /usr/local/bin/silverpilot-backend-entrypoint
RUN chmod 0555 /usr/local/bin/silverpilot-backend-entrypoint \
    && chmod 0444 /app/cecsms-serve.jar \
    && chmod -R a-w /app/knowledge-base /app/repository-media \
    && mkdir -p /app/logs /app/uploads \
    && chown silverpilot:silverpilot /app/logs /app/uploads

# Retain the original flattened runtime layout. Root is used only to prepare
# the Railway volume mount; the entrypoint drops privileges before Java starts.
FROM scratch AS runtime
COPY --from=runtime-rootfs / /
ENV JAVA_HOME=/opt/java/openjdk \
    JAVA_VERSION=jdk-25.0.3+9 \
    LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8 \
    PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    SPRING_PROFILES_ACTIVE=docker \
    PORT=8083 \
    CECSMS_SERVER_PORT=8083 \
    CECSMS_KNOWLEDGE_BASE_PATH=/app/knowledge-base \
    CECSMS_LEGACY_ASSET_DIR=/app/repository-media \
    CECSMS_UPLOAD_DIR=/app/uploads \
    CECSMS_REDIS_ENABLED=true \
    CECSMS_REDIS_HEALTH_ENABLED=true \
    CECSMS_AI_MOCK_ENABLED=false
WORKDIR /app
USER root
EXPOSE 8083
ENTRYPOINT ["/usr/local/bin/silverpilot-backend-entrypoint"]
