# Use a Java 21 runtime
FROM eclipse-temurin:21-jdk-jammy

# Set working directory inside container
WORKDIR /app

# Copy the built jar into the container
COPY target/blitzfox.jar ./blitzfox.jar

# Environment variable for bot token
ENV TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}

# Run the bot
CMD ["java", "-jar", "blitzfox.jar"]
