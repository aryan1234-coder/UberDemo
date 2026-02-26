# Simple Dockerfile for RideApp Spring Boot application
# Builds a runnable image from the packaged jar

FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Copy maven wrapper and pom
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn

# Copy source
COPY src ./src

# Make mvnw executable
RUN chmod +x mvnw

# Build application
RUN ./mvnw -q -DskipTests package

# Run stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy jar (assumes artifact name rideApp-*.jar)
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8081

# Use environment variables for configuration (provided via --env-file)
ENTRYPOINT ["java","-jar","/app/app.jar"]

