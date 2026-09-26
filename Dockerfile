# Stage 1: Build Frontend React UI
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: Build Spring Boot Backend with Static Frontend Assets
FROM eclipse-temurin:21-jdk-alpine AS backend-builder
RUN apk add --no-cache maven
WORKDIR /app
COPY backend/pom.xml ./backend/
COPY backend/src ./backend/src

# Copy static frontend build output into Spring Boot static resources
COPY --from=frontend-builder /app/frontend/dist ./backend/src/main/resources/static

WORKDIR /app/backend
RUN mvn package -DskipTests

# Stage 3: Production Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
EXPOSE 8080
ENV PORT=8080
COPY --from=backend-builder /app/backend/target/*.jar app.jar

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
