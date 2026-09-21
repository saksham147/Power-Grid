# syntax=docker/dockerfile:1

# Multi-stage: the build stage needs a JDK and the Maven wrapper; the runtime image needs
# neither, just a JRE and the one jar spring-boot:repackage produces. Keeps the shipped image
# to a JRE + one jar instead of a full JDK + a downloaded Maven distribution.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Wrapper + POM first, source last: as long as pom.xml is unchanged, Docker reuses the
# dependency-download layer instead of re-fetching the internet on every source edit.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Explicit chmod: git stores mvnw as 100644 (a Windows checkout has no exec bit to record), so on any
# Linux/macOS/WSL clone or CI checkout the COPY above lands it non-executable and the next RUN
# dies with "./mvnw: Permission denied". Setting it here makes the build independent of the host.
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -o dependency:go-offline || true

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8090
# MaxRAMPercentage, not -Xmx: sizes the heap off whatever cgroup limit the container actually
# gets (docker-compose.yml's mem_limit), so this Dockerfile doesn't have to hardcode a number
# that would silently disagree with it. Left at the default without this, five JVMs each sizing
# a quarter of the *host's* memory is exactly how one runaway service starves the other four.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=55.0", "-jar", "app.jar"]
