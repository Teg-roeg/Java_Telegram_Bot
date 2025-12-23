# Use Java 21 runtime
FROM eclipse-temurin:21-jdk-jammy

# Set working directory inside container
WORKDIR /app

# Copy the built jar into the container
COPY target/blitzfox.jar ./blitzfox.jar

# Set environment variable for bot token
ENV TELEGRAM_BOT_TOKEN=8595821322:AAE7mtQZ5CuUXM3gkY0nE5qIE3iKl-q_Uss

# Run the bot
CMD ["java", "-jar", "blitzfox.jar"]
