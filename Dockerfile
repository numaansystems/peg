FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the packaged jar file
COPY target/peg-*.jar app.jar

# Expose the application port
EXPOSE 8080

# Set environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
