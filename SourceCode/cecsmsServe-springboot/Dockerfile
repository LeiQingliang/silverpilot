# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25.0.3_9-jdk-resolute@sha256:76b2c606540ac8de355f28f5a3979071456a13a531989d93ace4a2d995785ff1 AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw mvnw.cmd pom.xml ./
COPY src src
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
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 silverpilot \
    && useradd --system --uid 10001 --gid silverpilot --home-dir /app silverpilot
WORKDIR /app
COPY --from=build --chown=silverpilot:silverpilot /workspace/target/cecsms-serve-*.jar /app/cecsms-serve.jar
RUN mkdir -p /app/logs /app/uploads /app/knowledge-base \
    && chown -R silverpilot:silverpilot /app

# Flatten the prepared rootfs so the removed Ubuntu rock helper is absent from
# every delivered layer rather than merely hidden by an overlay whiteout.
FROM scratch AS runtime
COPY --from=runtime-rootfs / /
ENV JAVA_HOME=/opt/java/openjdk \
    JAVA_VERSION=jdk-25.0.3+9 \
    LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8 \
    PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
WORKDIR /app
USER silverpilot
EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/cecsms-serve.jar"]
