# Multi-stage build — the JDK + Maven toolchain never ships in the final image, only the jar.
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

COPY src ./src
# package (not verify) skips the Checkstyle gate bound to the verify phase — that check
# belongs in CI/dev (./mvnw verify), not in every container build.
RUN ./mvnw package -DskipTests -q

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /app/target/api-*.jar app.jar

EXPOSE 8000
CMD ["java", "-jar", "app.jar"]
