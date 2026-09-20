FROM maven:3.9-eclipse-temurin-21 AS BUILDER

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN mkdir -p /run/secrets/auth-keys

COPY --from=BUILDER /app/target/auth-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 4008

ENTRYPOINT ["java", "-jar", "app.jar"]
