FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -DskipTests package
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "target/clinica-0.0.1-SNAPSHOT.jar"]
