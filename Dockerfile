# Stage 1: Build the Vue 3 frontend
FROM node:20-alpine AS frontend-builder
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: Build the Spring Boot backend
FROM maven:3.9-eclipse-temurin-21 AS backend-builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Copy the built frontend static resources to Spring Boot's static folder
COPY --from=frontend-builder /frontend/dist ./src/main/resources/static
RUN mvn clean package -DskipTests

# Stage 3: Runtime JRE
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend-builder /app/target/*.jar app.jar

# Configure default environment variables
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
