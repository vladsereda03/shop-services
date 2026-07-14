# syntax=docker/dockerfile:1

# One parameterized Dockerfile for all six services:
#   docker build --build-arg MODULE=<auth|client|product|cart|order|payment> -t shop/<name> .

# ---- build stage: compile the module and its reactor dependencies ----
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /workspace

# Copy only the poms first: as long as they are unchanged, Docker reuses the
# cached dependency-download layer below across rebuilds AND across modules.
COPY pom.xml ./
COPY libs/contracts/pom.xml libs/contracts/
COPY services/auth/pom.xml services/auth/
COPY services/client/pom.xml services/client/
COPY services/product/pom.xml services/product/
COPY services/cart/pom.xml services/cart/
COPY services/order/pom.xml services/order/
COPY services/payment/pom.xml services/payment/
RUN mvn -B -ntp dependency:go-offline

COPY libs libs
COPY services services
RUN mvn -B -ntp -pl services/${MODULE} -am package -DskipTests

# Split the fat jar into layers so that a code change only invalidates the
# small "application" layer, not the dependency layers.
RUN java -Djarmode=tools -jar services/${MODULE}/target/*-SNAPSHOT.jar extract --layers --launcher --destination /extracted

# ---- runtime stage: JRE only, no Maven/JDK/sources ----
FROM eclipse-temurin:21-jre
RUN useradd --system spring
USER spring
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
