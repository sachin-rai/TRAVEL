FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=builder /build/target/flight-service.jar app.jar

EXPOSE 8082
ENV JAVA_OPTS=""
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8082/actuator/health/liveness || exit 1

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
