# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -o dependency:go-offline || true

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8093
# See Producer/Dockerfile's own comment: sizes the heap off the container's cgroup limit
# (docker-compose.yml's mem_limit) instead of the host's whole memory.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=55.0", "-jar", "app.jar"]
