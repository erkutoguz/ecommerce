FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./app

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=BUILDER /app/target/payment-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 4007

ENTRYPOINT ["java", "-jar", "app.jar"]