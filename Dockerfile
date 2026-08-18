FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY backend/ backend/
RUN chmod +x backend/mvnw && cd backend && ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/backend/target/backend-0.0.1-SNAPSHOT.jar /app/backend.jar

EXPOSE 8080
CMD ["java", "-jar", "/app/backend.jar"]
