# syntax=docker/dockerfile:1.7

FROM maven:3-eclipse-temurin-26 AS builder
ARG MAVEN_MIRROR=https://repo.maven.apache.org/maven2
WORKDIR /workspace
COPY pom.xml .
COPY .mvn/settings-docker.xml /tmp/settings-docker.xml
RUN sed -i "s|@@MAVEN_MIRROR@@|${MAVEN_MIRROR}|g" /tmp/settings-docker.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -s /tmp/settings-docker.xml -DskipTests dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -s /tmp/settings-docker.xml -DskipTests package

FROM eclipse-temurin:21-jre-jammy
ARG UBUNTU_MIRROR=http://ports.ubuntu.com/ubuntu-ports
RUN sed -i "s|http://ports.ubuntu.com/ubuntu-ports|${UBUNTU_MIRROR}|g" /etc/apt/sources.list \
    && apt-get -o Acquire::Retries=5 update \
    && apt-get install -y --no-install-recommends curl tesseract-ocr tesseract-ocr-eng tesseract-ocr-chi-sim \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 myrag \
    && useradd --uid 10001 --gid myrag --home-dir /app --create-home --shell /usr/sbin/nologin myrag
WORKDIR /app
COPY --from=builder --chown=myrag:myrag /workspace/target/app.jar app.jar
RUN install -d -o myrag -g myrag /app/data/db /app/data/imports
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseG1GC", "-jar", "/app/app.jar"]
