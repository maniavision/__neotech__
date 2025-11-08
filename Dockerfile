# --- Build Stage ---
# Use a Maven image with a stable JDK (e.g., Temurin 21) to build the app
FROM maven:3.9-eclipse-temurin-21 AS builder

# Set the working directory
WORKDIR /app

# Copy the pom.xml and download dependencies
# This caches the dependencies layer unless pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the rest of the source code and build the application
COPY src ./src
# Use -DskipTests to avoid running tests during the Docker build
# Tests should be a separate step in your CI pipeline
RUN mvn package -DskipTests

# --- Build Stage ---
FROM maven:3.9-eclipse-temurin-21-jammy AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# --- Run Stage ---
FROM gcr.io/distroless/java21-debian11
WORKDIR /app
COPY --from=builder /app/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]