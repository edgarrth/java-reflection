FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY payment-plugin-api ./payment-plugin-api
COPY payment-service ./payment-service
COPY provider-alpha ./provider-alpha
COPY provider-beta ./provider-beta
COPY explicit-example ./explicit-example
RUN mvn -B -ntp verify

FROM python:3.13-slim AS catalog
WORKDIR /workspace
COPY scripts/install_plugins.py ./scripts/install_plugins.py
COPY datasets/payment-plugins.json ./datasets/payment-plugins.json
COPY --from=build /workspace/provider-alpha/target/provider-alpha-1.0.0.jar ./provider-alpha/target/provider-alpha-1.0.0.jar
RUN python scripts/install_plugins.py --runtime /runtime --providers ALPHA

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/payment-service/target/payment-service-1.0.0.jar app.jar
COPY --from=catalog /runtime ./runtime
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
