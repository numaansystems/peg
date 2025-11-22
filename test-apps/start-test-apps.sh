#!/bin/bash

# Script to start both test applications

echo "Starting test applications..."

# Start Spring Boot app
cd spring-boot-app
echo "Starting Spring Boot Test App on port 8081..."
nohup java -jar target/spring-boot-test-app-1.0.0-SNAPSHOT.jar > spring-boot-app.log 2>&1 &
SPRING_BOOT_PID=$!
echo $SPRING_BOOT_PID > spring-boot-app.pid
echo "Spring Boot app started with PID: $SPRING_BOOT_PID"
cd ..

# Start Legacy app
cd legacy-app
echo "Starting Legacy Test App on port 8082..."
nohup java -jar target/legacy-test-app-1.0.0-SNAPSHOT.jar > legacy-app.log 2>&1 &
LEGACY_PID=$!
echo $LEGACY_PID > legacy-app.pid
echo "Legacy app started with PID: $LEGACY_PID"
cd ..

echo ""
echo "Both applications started!"
echo ""
echo "Spring Boot App: http://localhost:8081/"
echo "Legacy App: http://localhost:8082/"
echo ""
echo "Via Gateway (requires gateway to be running):"
echo "Spring Boot App: http://localhost:8080/api/"
echo "Legacy App: http://localhost:8080/legacy/"
echo ""
echo "To stop the applications, run: ./stop-test-apps.sh"
