FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY jar/blitzfox.jar ./blitzfox.jar
CMD ["java", "-jar", "blitzfox.jar"]