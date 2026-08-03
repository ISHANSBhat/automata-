# Stage 1: Build the Java backend
FROM eclipse-temurin:21-jdk-alpine AS builder

# Set the working directory
WORKDIR /app

# Copy the backend source files
COPY backend/src backend/src

# Compile the Java backend files to backend/out
RUN mkdir -p backend/out && \
    find backend/src -name "*.java" > sources.txt && \
    javac -d backend/out @sources.txt

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine

# Set the working directory
WORKDIR /app

# Copy the compiled .class files from the builder stage
COPY --from=builder /app/backend/out backend/out

# Copy the frontend static files
# The Java server looks for "frontend" relative to user.dir
COPY frontend frontend

# Default port configuration (matches EXPOSE)
ENV PORT=8080
EXPOSE 8080

# Run the Java server
CMD ["java", "-cp", "backend/out", "automata.server.AutomataServer"]
