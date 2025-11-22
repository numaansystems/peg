#!/bin/bash

# Script to stop both test applications

echo "Stopping test applications..."

# Stop Spring Boot app
if [ -f spring-boot-app/spring-boot-app.pid ]; then
    SPRING_BOOT_PID=$(cat spring-boot-app/spring-boot-app.pid)
    if ps -p $SPRING_BOOT_PID > /dev/null; then
        echo "Stopping Spring Boot app (PID: $SPRING_BOOT_PID)..."
        kill $SPRING_BOOT_PID
        rm spring-boot-app/spring-boot-app.pid
    else
        echo "Spring Boot app not running"
        rm spring-boot-app/spring-boot-app.pid
    fi
else
    echo "Spring Boot app PID file not found"
fi

# Stop Legacy app
if [ -f legacy-app/legacy-app.pid ]; then
    LEGACY_PID=$(cat legacy-app/legacy-app.pid)
    if ps -p $LEGACY_PID > /dev/null; then
        echo "Stopping Legacy app (PID: $LEGACY_PID)..."
        kill $LEGACY_PID
        rm legacy-app/legacy-app.pid
    else
        echo "Legacy app not running"
        rm legacy-app/legacy-app.pid
    fi
else
    echo "Legacy app PID file not found"
fi

echo "Done!"
